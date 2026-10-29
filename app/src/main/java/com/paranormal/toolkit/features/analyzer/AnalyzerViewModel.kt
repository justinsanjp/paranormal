package com.paranormal.toolkit.features.analyzer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paranormal.toolkit.data.Recording
import com.paranormal.toolkit.features.analyzer.dsp.BiquadFilter
import com.paranormal.toolkit.features.analyzer.dsp.FilterType
import com.paranormal.toolkit.features.analyzer.dsp.Normalizer
import com.paranormal.toolkit.features.analyzer.dsp.Spectrogram
import com.paranormal.toolkit.features.analyzer.dsp.SpectrogramComputer
import com.paranormal.toolkit.features.recorder.RecordingsRepository
import com.paranormal.toolkit.features.recorder.WavFileWriter
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalyzerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecordingsRepository(application)
    private val player = AudioPlayer()

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings

    private val _selected = MutableStateFlow<Recording?>(null)
    val selected: StateFlow<Recording?> = _selected

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var sampleRateHz: Int = 44_100
    private var originalSamples: FloatArray = FloatArray(0)
    private var workingSamples: FloatArray = FloatArray(0)

    private val _waveform = MutableStateFlow(FloatArray(0))
    val waveform: StateFlow<FloatArray> = _waveform

    private val _spectrogram = MutableStateFlow<Spectrogram?>(null)
    val spectrogram: StateFlow<Spectrogram?> = _spectrogram

    private val _filterType = MutableStateFlow<FilterType?>(null)
    val filterType: StateFlow<FilterType?> = _filterType

    private val _cutoffHz = MutableStateFlow(1000f)
    val cutoffHz: StateFlow<Float> = _cutoffHz

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    /** Set when a clip had to be downsampled to fit the Analyzer's sample budget. */
    private val _downsampleNote = MutableStateFlow<String?>(null)
    val downsampleNote: StateFlow<String?> = _downsampleNote

    init {
        refreshRecordings()
    }

    fun refreshRecordings() {
        viewModelScope.launch {
            _recordings.value = withContext(Dispatchers.IO) { repository.listRecordings() }
        }
    }

    fun select(recording: Recording) {
        _selected.value = recording
        _filterType.value = null
        _isDirty.value = false
        _isLoading.value = true
        player.stop()
        _isPlaying.value = false
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) { WavDecoder.decode(recording.file) }
            originalSamples = decoded.samples
            workingSamples = decoded.samples
            sampleRateHz = decoded.sampleRateHz
            _downsampleNote.value = if (decoded.isDownsampled) {
                "Lange Aufnahme: zur Analyse auf ${decoded.sampleRateHz} Hz heruntergerechnet " +
                    "(Original ${decoded.sourceSampleRateHz} Hz). Die Datei selbst bleibt unverändert."
            } else {
                null
            }
            recomputeVisuals()
            _isLoading.value = false
        }
    }

    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = withContext(Dispatchers.IO) { runCatching { repository.importAudio(uri) } }
            _isImporting.value = false
            result
                .onSuccess { imported ->
                    val recordings = withContext(Dispatchers.IO) { repository.listRecordings() }
                    _recordings.value = recordings
                    recordings.firstOrNull { it.file.absolutePath == imported.file.absolutePath }
                        ?.let { select(it) }
                }
                .onFailure { _errorMessage.value = it.message ?: "Import fehlgeschlagen." }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun setFilter(type: FilterType?) {
        _filterType.value = type
        applyProcessing()
    }

    fun setCutoff(hz: Float) {
        _cutoffHz.value = hz
        if (_filterType.value != null) applyProcessing()
    }

    fun applyPeakNormalize() {
        workingSamples = Normalizer.peakNormalize(workingSamples)
        _isDirty.value = true
        viewModelScope.launch { recomputeVisuals() }
    }

    fun applyRmsNormalize() {
        workingSamples = Normalizer.rmsNormalize(workingSamples)
        _isDirty.value = true
        viewModelScope.launch { recomputeVisuals() }
    }

    fun resetToOriginal() {
        workingSamples = originalSamples
        _filterType.value = null
        _isDirty.value = false
        viewModelScope.launch { recomputeVisuals() }
    }

    fun setSpeed(value: Float) {
        _speed.value = value
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            player.stop()
            _isPlaying.value = false
        } else {
            player.play(workingSamples, sampleRateHz, _speed.value)
            _isPlaying.value = true
        }
    }

    fun saveAsNewRecording() {
        if (workingSamples.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val outFile = repository.newRecordingFile()
            val writer = WavFileWriter(outFile, sampleRateHz, channels = 1, bitsPerSample = 16)
            val shorts = ShortArray(workingSamples.size) { i ->
                (workingSamples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            writer.write(shorts, shorts.size)
            writer.close()
            refreshRecordings()
        }
    }

    private fun applyProcessing() {
        val type = _filterType.value
        workingSamples = if (type == null) {
            originalSamples
        } else {
            BiquadFilter(type, _cutoffHz.value, sampleRateHz).apply(originalSamples)
        }
        _isDirty.value = type != null
        viewModelScope.launch { recomputeVisuals() }
    }

    private suspend fun recomputeVisuals() {
        val samples = workingSamples
        val rate = sampleRateHz
        val (waveformPeaks, spectro) = withContext(Dispatchers.Default) {
            val peaks = downsampleForWaveform(samples)
            val spec = if (samples.isNotEmpty()) {
                val fftSize = 1024
                val targetFrames = 800
                val hopSize = max(fftSize / 4, samples.size / targetFrames)
                SpectrogramComputer.compute(samples, rate, fftSize, hopSize)
            } else {
                null
            }
            peaks to spec
        }
        _waveform.value = waveformPeaks
        _spectrogram.value = spectro
    }

    private fun downsampleForWaveform(samples: FloatArray, buckets: Int = 400): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        val bucketSize = max(1, samples.size / buckets)
        val result = FloatArray(minOf(buckets, samples.size))
        for (i in result.indices) {
            var peak = 0f
            val start = i * bucketSize
            val end = minOf(start + bucketSize, samples.size)
            for (j in start until end) peak = maxOf(peak, abs(samples[j]))
            result[i] = peak
        }
        return result
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }
}
