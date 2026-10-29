package com.paranormal.toolkit.features.ghostapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.paranormal.toolkit.MainActivity
import com.paranormal.toolkit.R
import com.paranormal.toolkit.data.Language
import com.paranormal.toolkit.data.SpokenWord
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns a GhostApp session independently of any Activity, so a session keeps
 * running with the screen off or the app backgrounded - how the app is
 * actually used in the field. A partial wake lock keeps the SoC awake,
 * because the motion and magnetic-field sensors this feeds on are not
 * wake-up sensors and would otherwise stop delivering during suspend.
 */
class GhostAppService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Never fed with sensor data, by design: the sensors decide when a word
     * falls, this decides which one. Keeping the two apart is what stops a
     * sensor reading from being able to pull up a particular word.
     */
    private val secureRandom = SecureRandom()

    private val triggerEngine by lazy { SensorTriggerEngine(this) }
    private val wordDatabase by lazy { WordDatabase(this) }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): GhostAppService = this@GhostAppService
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _history = MutableStateFlow<List<SpokenWord>>(emptyList())
    val history: StateFlow<List<SpokenWord>> = _history

    /** An optional list the user imported; empty means the built-in [WordBank] is used. */
    private val _customWords = MutableStateFlow(WordBankState.EMPTY)
    val customWords: StateFlow<WordBankState> = _customWords

    private val _language = MutableStateFlow(Language.DE)
    val language: StateFlow<Language> = _language

    private val _sensitivity = MutableStateFlow(0.5f)
    val sensitivity: StateFlow<Float> = _sensitivity

    val hasSensors: Boolean get() = triggerEngine.hasSensors

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            applyTtsLanguage()
        }
        reloadCustomWords()
        serviceScope.launch {
            triggerEngine.ticks.collect { tick -> onTick(tick) }
        }
    }

    fun reloadCustomWords() {
        _customWords.value = wordDatabase.load()
    }

    fun setLanguage(language: Language) {
        _language.value = language
        applyTtsLanguage()
    }

    fun setSensitivity(value: Float) {
        _sensitivity.value = value
        triggerEngine.sensitivity = value
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun startSession() {
        if (_isRunning.value) return
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        triggerEngine.sensitivity = _sensitivity.value
        triggerEngine.start()
        _isRunning.value = true
    }

    fun stopSession() {
        if (!_isRunning.value) return
        triggerEngine.stop()
        releaseWakeLock()
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        triggerEngine.stop()
        releaseWakeLock()
        tts?.stop()
        tts?.shutdown()
        tts = null
        serviceScope.cancel()
        super.onDestroy()
    }

    /** The active word database: the user's own list if there is one, else the built-in bank. */
    private fun activeEntries(): List<String> {
        val custom = _customWords.value
        return if (custom.isLoaded) custom.entries else WordBank.forLanguage(_language.value)
    }

    /**
     * A tick says only that something happened, and when. The entry is then
     * drawn by [secureRandom] alone - [TriggerTick.energy] is recorded as
     * metadata for the log and never reaches into the list.
     */
    private fun onTick(tick: TriggerTick) {
        val entries = activeEntries()
        if (entries.isEmpty()) return
        val entry = entries[secureRandom.nextInt(entries.size)]
        _history.value = _history.value + SpokenWord(
            text = entry,
            language = _language.value,
            timestampMillis = tick.atMillis,
            triggerEnergy = tick.energy,
        )
        speak(entry)
    }

    private fun speak(entry: String) {
        if (!ttsReady) return
        tts?.speak(entry, TextToSpeech.QUEUE_ADD, null, "entry-${System.nanoTime()}")
    }

    private fun applyTtsLanguage() {
        val locale = if (_language.value == Language.DE) Locale.GERMAN else Locale.US
        tts?.language = locale
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.ghostapp_notification_title))
            .setContentText(getString(R.string.notification_tap_to_return))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.ghostapp_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ghostapp_session"
        private const val NOTIFICATION_ID = 1002
        private const val WAKE_LOCK_TAG = "paranormal:ghostapp"

        /** Safety net so a forgotten session can't drain the battery overnight. */
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 8L * 60 * 60 * 1000
    }
}
