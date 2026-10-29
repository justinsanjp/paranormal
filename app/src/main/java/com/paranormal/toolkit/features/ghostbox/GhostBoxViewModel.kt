package com.paranormal.toolkit.features.ghostbox

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paranormal.toolkit.features.ghostbox.rtlsdr.RtlSdrDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class GhostBoxMode { SCAN, MANUAL }

/** Which real signal path GhostBox currently drives. */
enum class RadioBackend { USB_SDR, COMPANION_APP }

/**
 * Orchestrates two independent real-hardware signal paths - a USB RTL-SDR
 * dongle we demodulate ourselves, or a companion app (e.g. a phone's own
 * Xiaomi-signed FM radio app) that we steer indirectly via standard
 * media-button key events. Both only ever reflect what's actually coming
 * off real hardware; neither path fabricates a result when nothing is
 * connected/running.
 */
class GhostBoxViewModel(application: Application) : AndroidViewModel(application) {

    private val usbController = UsbController(application)
    val connectionState: StateFlow<UsbConnectionState> = usbController.state

    private val companionController = CompanionFmController(
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
    )

    private var sdrDevice: RtlSdrDevice? = null
    private var audioSink: LiveAudioSink? = null
    private var readJob: Job? = null
    private var companionScanJob: Job? = null

    private val _backend = MutableStateFlow(RadioBackend.USB_SDR)
    val backend: StateFlow<RadioBackend> = _backend

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _isCompanionScanning = MutableStateFlow(false)
    val isCompanionScanning: StateFlow<Boolean> = _isCompanionScanning

    private val _mode = MutableStateFlow(GhostBoxMode.SCAN)
    val mode: StateFlow<GhostBoxMode> = _mode

    private val _frequencyHz = MutableStateFlow(SCAN_MIN_HZ)
    val frequencyHz: StateFlow<Long> = _frequencyHz

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        usbController.start()
    }

    fun setBackend(backend: RadioBackend) {
        if (_backend.value == backend) return
        stopListening()
        stopCompanionScan()
        _backend.value = backend
    }

    fun companionSeekNext() = companionController.seekNext()
    fun companionSeekPrevious() = companionController.seekPrevious()
    fun companionTogglePlayback() = companionController.togglePlayback()

    fun toggleCompanionScan() {
        if (_isCompanionScanning.value) stopCompanionScan() else startCompanionScan()
    }

    private fun startCompanionScan() {
        _isCompanionScanning.value = true
        companionScanJob = viewModelScope.launch {
            while (isActive) {
                companionController.seekNext()
                delay(COMPANION_SCAN_DWELL_MILLIS)
            }
        }
    }

    private fun stopCompanionScan() {
        companionScanJob?.cancel()
        companionScanJob = null
        _isCompanionScanning.value = false
    }

    fun requestPermission() {
        val state = connectionState.value
        if (state is UsbConnectionState.PermissionRequired) usbController.requestPermission(state.device)
    }

    fun setMode(mode: GhostBoxMode) {
        _mode.value = mode
    }

    fun setFrequency(hz: Long) {
        _frequencyHz.value = hz
        sdrDevice?.tune(hz)
    }

    fun toggleListening() {
        if (_isListening.value) stopListening() else startListening()
    }

    private fun startListening() {
        val state = connectionState.value
        if (state !is UsbConnectionState.Ready) return

        val connection = usbController.openConnection(state.device)
        if (connection == null) {
            _errorMessage.value = "USB-Verbindung konnte nicht geöffnet werden."
            return
        }
        val usbInterface = state.device.getInterface(0)
        val device = runCatching { RtlSdrDevice(connection, usbInterface) }.getOrElse {
            _errorMessage.value = "Gerät konnte nicht initialisiert werden: ${it.message}"
            return
        }
        sdrDevice = device
        device.tune(_frequencyHz.value)
        _errorMessage.value = null

        val outSampleRate = 24_000
        val sink = LiveAudioSink(outSampleRate)
        audioSink = sink
        sink.start()
        _isListening.value = true

        readJob = viewModelScope.launch(Dispatchers.IO) {
            val demodulator = FmDemodulator(RtlSdrDevice.SAMPLE_RATE_HZ)
            val decimator = Decimator(RtlSdrDevice.SAMPLE_RATE_HZ / outSampleRate)
            val buffer = ByteArray(64 * 1024)
            var lastHopAt = System.currentTimeMillis()
            var scanFreq = SCAN_MIN_HZ

            while (isActive) {
                if (_mode.value == GhostBoxMode.SCAN) {
                    val now = System.currentTimeMillis()
                    if (now - lastHopAt >= SCAN_DWELL_MILLIS) {
                        scanFreq += SCAN_STEP_HZ
                        if (scanFreq > SCAN_MAX_HZ) scanFreq = SCAN_MIN_HZ
                        device.tune(scanFreq)
                        _frequencyHz.value = scanFreq
                        lastHopAt = now
                    }
                }
                val read = device.readSamples(buffer)
                if (read > 0) {
                    val demodulated = demodulator.demodulate(buffer, read)
                    val decimated = decimator.decimate(demodulated)
                    sink.write(decimated)
                }
            }
        }
    }

    private fun stopListening() {
        readJob?.cancel()
        readJob = null
        audioSink?.stop()
        audioSink = null
        sdrDevice?.close()
        sdrDevice = null
        _isListening.value = false
    }

    override fun onCleared() {
        stopListening()
        stopCompanionScan()
        usbController.stop()
        super.onCleared()
    }

    companion object {
        const val SCAN_MIN_HZ = 87_500_000L
        const val SCAN_MAX_HZ = 108_000_000L
        const val SCAN_STEP_HZ = 200_000L
        const val SCAN_DWELL_MILLIS = 250L

        // Real hardware seeking takes noticeably longer than our own USB-SDR
        // sweep: the tuner has to lock and the companion app has to settle
        // before another "next" makes sense.
        const val COMPANION_SCAN_DWELL_MILLIS = 3_000L
    }
}
