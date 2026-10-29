package com.paranormal.toolkit.features.ghostapp

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paranormal.toolkit.data.Language
import com.paranormal.toolkit.data.SpokenWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Thin proxy in front of [GhostAppService]: the session itself lives in the
 * service so it survives the screen going off, this only mirrors its state
 * for the UI and forwards user actions.
 */
class GhostAppViewModel(application: Application) : AndroidViewModel(application) {

    private val wordDatabase = WordDatabase(application)

    private var service: GhostAppService? = null
    private var bound = false
    private var startRequested = false
    private val mirrorJobs = mutableListOf<Job>()

    val hasSensors: Boolean = SensorTriggerEngine.deviceHasTriggerSensors(application)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _language = MutableStateFlow(Language.DE)
    val language: StateFlow<Language> = _language

    private val _sensitivity = MutableStateFlow(0.5f)
    val sensitivity: StateFlow<Float> = _sensitivity

    private val _history = MutableStateFlow<List<SpokenWord>>(emptyList())
    val history: StateFlow<List<SpokenWord>> = _history

    /** Empty unless the user imported their own list; then it replaces [WordBank]. */
    private val _customWords = MutableStateFlow(WordBankState.EMPTY)
    val customWords: StateFlow<WordBankState> = _customWords

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = (binder as GhostAppService.LocalBinder).getService()
            service = local
            bound = true
            local.setSensitivity(_sensitivity.value)
            local.setLanguage(_language.value)
            local.reloadCustomWords()
            mirror(local)
            if (startRequested) {
                startRequested = false
                startSession()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            mirrorJobs.forEach { it.cancel() }
            mirrorJobs.clear()
        }
    }

    init {
        _customWords.value = wordDatabase.load()
        val ctx = getApplication<Application>()
        ctx.bindService(
            Intent(ctx, GhostAppService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun mirror(service: GhostAppService) {
        mirrorJobs += viewModelScope.launch { service.isRunning.collect { _isRunning.value = it } }
        mirrorJobs += viewModelScope.launch { service.history.collect { _history.value = it } }
        mirrorJobs += viewModelScope.launch { service.customWords.collect { _customWords.value = it } }
    }

    fun setLanguage(language: Language) {
        _language.value = language
        service?.setLanguage(language)
    }

    fun setSensitivity(value: Float) {
        _sensitivity.value = value
        service?.setSensitivity(value)
    }

    fun clearHistory() {
        val current = service
        if (current != null) current.clearHistory() else _history.value = emptyList()
    }

    fun toggleSession() {
        if (_isRunning.value) {
            service?.stopSession()
        } else {
            startSession()
        }
    }

    /** Optional: replace the built-in word bank with the user's own text file. */
    fun importWordDatabase(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = withContext(Dispatchers.IO) { runCatching { wordDatabase.import(uri) } }
            _isImporting.value = false
            result
                .onSuccess { state ->
                    _customWords.value = state
                    service?.reloadCustomWords()
                }
                .onFailure { _errorMessage.value = it.message ?: "Import fehlgeschlagen." }
        }
    }

    /** Drops the imported list and falls back to the built-in word bank. */
    fun clearWordDatabase() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { wordDatabase.clear() }
            _customWords.value = WordBankState.EMPTY
            service?.reloadCustomWords()
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    private fun startSession() {
        val ctx = getApplication<Application>()
        ContextCompat.startForegroundService(ctx, Intent(ctx, GhostAppService::class.java))
        val current = service
        if (current == null) {
            startRequested = true
            return
        }
        current.startSession()
    }

    override fun onCleared() {
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
