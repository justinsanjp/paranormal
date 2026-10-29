package com.paranormal.toolkit.features.recorder

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
import com.paranormal.toolkit.data.Recording
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecorderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecordingsRepository(application)

    private var service: RecordingService? = null
    private var bound = false
    private var pendingStartFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _levelDb = MutableStateFlow(-96f)
    val levelDb: StateFlow<Float> = _levelDb

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis

    private val _gain = MutableStateFlow(4f)
    val gain: StateFlow<Float> = _gain

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = (binder as RecordingService.LocalBinder).getService()
            service = local
            bound = true
            local.engine.gain = _gain.value
            observeEngine(local)
            pendingStartFile?.let { file ->
                local.startRecording(file)
                pendingStartFile = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    init {
        val ctx = getApplication<Application>()
        ctx.bindService(
            Intent(ctx, RecordingService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        refreshRecordings()
    }

    private fun observeEngine(service: RecordingService) {
        viewModelScope.launch {
            service.engine.isRecording.collect { recording ->
                _isRecording.value = recording
                if (!recording) refreshRecordings()
            }
        }
        viewModelScope.launch { service.engine.levelDb.collect { _levelDb.value = it } }
        viewModelScope.launch { service.engine.elapsedMillis.collect { _elapsedMillis.value = it } }
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            service?.stopRecording()
            return
        }
        val ctx = getApplication<Application>()
        val file = repository.newRecordingFile()
        ContextCompat.startForegroundService(ctx, Intent(ctx, RecordingService::class.java))
        val current = service
        if (current != null) {
            current.startRecording(file)
        } else {
            pendingStartFile = file
        }
    }

    fun setGain(value: Float) {
        _gain.value = value
        service?.engine?.gain = value
    }

    /** Pulls an audio file from the user's storage into the recordings list. */
    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = withContext(Dispatchers.IO) { runCatching { repository.importAudio(uri) } }
            _isImporting.value = false
            result.onFailure { _errorMessage.value = it.message ?: "Import fehlgeschlagen." }
            refreshRecordings()
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun refreshRecordings() {
        viewModelScope.launch {
            _recordings.value = withContext(Dispatchers.IO) { repository.listRecordings() }
        }
    }

    fun deleteRecording(recording: Recording) {
        repository.delete(recording)
        refreshRecordings()
    }

    override fun onCleared() {
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
