package com.paranormal.toolkit.features.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import java.io.File
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Captures raw microphone audio with the phone's automatic gain control,
 * noise suppression and echo cancellation switched off wherever the device
 * allows it, so quiet sounds are not auto-leveled away before they're even
 * written to disk - that suppression is exactly what a normal voice
 * recorder app does and exactly what defeats EVP work. A manual,
 * user-controlled digital gain is applied on top for extra sensitivity.
 */
class AudioRecorderEngine(private val scope: CoroutineScope) {

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var recordJob: Job? = null
    private var wavWriter: WavFileWriter? = null

    private var sampleRateHz: Int = 48_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    /** Linear multiplier applied to every sample. 1x = unity gain, up to 20x boost. */
    var gain: Float = 4f

    private val _levelDb = MutableStateFlow(-96f)
    val levelDb: StateFlow<Float> = _levelDb

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis

    var lastOutputFile: File? = null
        private set

    @SuppressLint("MissingPermission")
    fun start(outputFile: File) {
        if (_isRecording.value) return

        val bufferSize = pickBufferSize()
        val record = createAudioRecord(bufferSize) ?: return
        audioRecord = record
        disableAutoProcessing(record.audioSessionId)

        wavWriter = WavFileWriter(outputFile, sampleRateHz, channels = 1, bitsPerSample = 16)
        lastOutputFile = outputFile

        record.startRecording()
        _isRecording.value = true
        val startedAt = System.currentTimeMillis()

        recordJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            while (_isRecording.value) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    applyGain(buffer, read)
                    wavWriter?.write(buffer, read)
                    _levelDb.value = calculateDbFs(buffer, read)
                    _elapsedMillis.value = System.currentTimeMillis() - startedAt
                }
            }
        }
    }

    fun stop() {
        if (!_isRecording.value) return
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null

        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null

        noiseSuppressor?.release(); noiseSuppressor = null
        agc?.release(); agc = null
        echoCanceler?.release(); echoCanceler = null

        wavWriter?.close()
        wavWriter = null
        _levelDb.value = -96f
        _elapsedMillis.value = 0L
    }

    private fun pickBufferSize(): Int {
        for (rate in intArrayOf(48_000, 44_100)) {
            val minBuf = AudioRecord.getMinBufferSize(rate, channelConfig, encoding)
            if (minBuf > 0) {
                sampleRateHz = rate
                return minBuf * 4 // headroom so disk writes never cause an underrun
            }
        }
        sampleRateHz = 44_100
        return AudioRecord.getMinBufferSize(44_100, channelConfig, encoding).coerceAtLeast(4096)
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(bufferSize: Int): AudioRecord? {
        // UNPROCESSED gives the rawest possible signal (no AGC/NS/AEC at the
        // HAL level); VOICE_RECOGNITION is the next-best fallback since OEMs
        // are required to keep it free of AGC/NS too; MIC is the last resort.
        val sourcesInPriorityOrder = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        for (source in sourcesInPriorityOrder) {
            val record = runCatching {
                AudioRecord(source, sampleRateHz, channelConfig, encoding, bufferSize)
            }.getOrNull() ?: continue
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        return null
    }

    /** Belt-and-braces: some OEMs bundle AGC/NS/AEC even on supposedly raw sources. */
    private fun disableAutoProcessing(sessionId: Int) {
        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(sessionId)?.apply { enabled = false }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = false }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = false }
        }
    }

    private fun applyGain(buffer: ShortArray, length: Int) {
        val g = gain
        if (g == 1f) return
        for (i in 0 until length) {
            val boosted = buffer[i] * g
            buffer[i] = boosted.coerceIn(
                Short.MIN_VALUE.toFloat(),
                Short.MAX_VALUE.toFloat(),
            ).toInt().toShort()
        }
    }

    private fun calculateDbFs(buffer: ShortArray, length: Int): Float {
        var sumSquares = 0.0
        for (i in 0 until length) {
            val normalized = buffer[i] / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / length)
        if (rms <= 0.0) return -96f
        return max(-96f, (20 * log10(rms)).toFloat())
    }
}
