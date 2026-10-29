package com.paranormal.toolkit.features.recorder

import android.content.Context
import android.net.Uri
import com.paranormal.toolkit.data.Recording
import com.paranormal.toolkit.data.documentDisplayName
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Locale

/** Recordings live in app-specific external storage - no storage permission needed on API 26+. */
class RecordingsRepository(private val context: Context) {

    private val recordingsDir: File
        get() = File(context.getExternalFilesDir(null), "evp_recordings").apply { mkdirs() }

    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.GERMANY)

    fun newRecordingFile(): File {
        val name = "EVP_${fileNameFormat.format(System.currentTimeMillis())}.wav"
        return File(recordingsDir, name)
    }

    /**
     * Converts an audio file the user picked from their own storage into the
     * mono 16-bit WAV the recorder produces, so the Analyzer can treat it
     * like any other recording. A half-written file is cleaned up on failure.
     */
    fun importAudio(uri: Uri): ImportedAudio {
        val sourceName = documentDisplayName(context, uri)
        val target = importedRecordingFile(sourceName)
        return runCatching { AudioImporter.importToWav(context, uri, target) }
            .onFailure { target.delete() }
            .getOrThrow()
    }

    private fun importedRecordingFile(sourceName: String): File {
        val base = sanitizeFileName(sourceName.substringBeforeLast('.'))
        var candidate = File(recordingsDir, "IMPORT_$base.wav")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(recordingsDir, "IMPORT_${base}_$counter.wav")
            counter++
        }
        return candidate
    }

    private fun sanitizeFileName(name: String): String =
        name.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .take(48)
            .trim('_')
            .ifBlank { "Import" }

    fun listRecordings(): List<Recording> {
        val files = recordingsDir.listFiles { f -> f.extension.equals("wav", ignoreCase = true) }
            ?: emptyArray()
        return files
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val sampleRate = readWavSampleRate(file)
                Recording(
                    file = file,
                    name = file.nameWithoutExtension,
                    timestampMillis = file.lastModified(),
                    durationMillis = estimateWavDurationMillis(file, sampleRate),
                    sampleRateHz = sampleRate,
                )
            }
    }

    fun delete(recording: Recording): Boolean = recording.file.delete()

    private fun readWavSampleRate(file: File): Int = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(24)
            val bytes = ByteArray(4)
            raf.readFully(bytes)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
        }
    }.getOrDefault(44_100)

    private fun estimateWavDurationMillis(file: File, sampleRateHz: Int): Long {
        val dataBytes = (file.length() - 44).coerceAtLeast(0)
        val bytesPerSecond = sampleRateHz * 2 // mono, 16-bit
        if (bytesPerSecond == 0) return 0
        return dataBytes * 1000 / bytesPerSecond
    }
}
