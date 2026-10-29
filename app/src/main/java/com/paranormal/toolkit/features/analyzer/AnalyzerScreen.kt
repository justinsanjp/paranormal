package com.paranormal.toolkit.features.analyzer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paranormal.toolkit.data.Recording
import com.paranormal.toolkit.features.analyzer.dsp.FilterType
import com.paranormal.toolkit.features.analyzer.dsp.Spectrogram
import com.paranormal.toolkit.ui.theme.AlertRed
import com.paranormal.toolkit.ui.theme.SpectralGreen
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AnalyzerScreen(contentPadding: PaddingValues, viewModel: AnalyzerViewModel = viewModel()) {
    val recordings by viewModel.recordings.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importAudio) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "EVP Analyzer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                } else {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Audio laden")
                    }
                }
            }

            errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, color = AlertRed, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = viewModel::dismissError) { Text("OK") }
            }

            Spacer(Modifier.height(12.dp))

            if (recordings.isEmpty()) {
                Text(
                    "Noch keine Aufnahmen. Nimm im EVP Recorder eine Session auf oder lade " +
                        "eine Audiodatei aus dem Speicher - mp3, m4a, ogg, flac und wav gehen " +
                        "alle, solange das Gerät sie abspielen kann.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recordings, key = { it.file.absolutePath }) { recording ->
                        RecordingChip(
                            recording = recording,
                            selected = recording.file.absolutePath == selected?.file?.absolutePath,
                            onClick = { viewModel.select(recording) },
                        )
                    }
                }
            }
        }

        when {
            recordings.isEmpty() -> Unit
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            selected == null -> {
                Text(
                    "Wähle oben eine Aufnahme zum Bearbeiten.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            else -> AnalyzerWorkspace(viewModel)
        }
    }
}

@Composable
private fun AnalyzerWorkspace(viewModel: AnalyzerViewModel) {
    val waveform by viewModel.waveform.collectAsState()
    val spectrogram by viewModel.spectrogram.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val cutoffHz by viewModel.cutoffHz.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val downsampleNote by viewModel.downsampleNote.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        downsampleNote?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Text("Waveform", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        WaveformView(waveform)

        Spacer(Modifier.height(16.dp))
        Text("Spektrogramm", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        SpectrogramView(spectrogram)

        Spacer(Modifier.height(20.dp))
        Text("Filter", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filterType == null, onClick = { viewModel.setFilter(null) }, label = { Text("Aus") })
            FilterChip(
                selected = filterType == FilterType.HIGH_PASS,
                onClick = { viewModel.setFilter(FilterType.HIGH_PASS) },
                label = { Text("Hochpass") },
            )
            FilterChip(
                selected = filterType == FilterType.LOW_PASS,
                onClick = { viewModel.setFilter(FilterType.LOW_PASS) },
                label = { Text("Tiefpass") },
            )
            FilterChip(
                selected = filterType == FilterType.BAND_PASS,
                onClick = { viewModel.setFilter(FilterType.BAND_PASS) },
                label = { Text("Bandpass") },
            )
        }
        if (filterType != null) {
            Text("Grenzfrequenz: ${cutoffHz.roundToInt()} Hz")
            Slider(value = cutoffHz, onValueChange = viewModel::setCutoff, valueRange = 100f..8000f)
        }

        Spacer(Modifier.height(16.dp))
        Text("Lautstärke", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::applyPeakNormalize) { Text("Peak normalisieren") }
            OutlinedButton(onClick = viewModel::applyRmsNormalize) { Text("RMS normalisieren") }
            if (isDirty) {
                TextButton(onClick = viewModel::resetToOriginal) { Text("Zurücksetzen") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Wiedergabegeschwindigkeit: ${String.format(Locale.GERMANY, "%.2fx", speed)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Langsamer abspielen (Tonhöhe bleibt gleich) macht leise/schnelle EVPs besser hörbar.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(value = speed, onValueChange = viewModel::setSpeed, valueRange = 0.25f..2f)

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::togglePlayback) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isPlaying) "Stopp" else "Abspielen")
            }
            if (isDirty) {
                OutlinedButton(onClick = viewModel::saveAsNewRecording) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Als neue Aufnahme speichern")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RecordingChip(recording: Recording, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(recording.name) })
}

@Composable
private fun WaveformView(peaks: FloatArray) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        drawRect(color = Color(0xFF15151D), size = size)
        if (peaks.isEmpty()) return@Canvas
        val midY = size.height / 2
        val stepX = size.width / peaks.size
        for (i in peaks.indices) {
            val barHeight = peaks[i].coerceIn(0f, 1f) * midY
            val x = i * stepX + stepX / 2
            drawLine(
                color = SpectralGreen,
                start = Offset(x, midY - barHeight),
                end = Offset(x, midY + barHeight),
                strokeWidth = stepX.coerceAtLeast(1f),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SpectrogramView(spectrogram: Spectrogram?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        if (spectrogram == null) {
            Text(
                "Kein Signal",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val bitmap = remember(spectrogram) { SpectrogramRenderer.render(spectrogram) }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Spektrogramm",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
