package com.paranormal.toolkit.features.recorder

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paranormal.toolkit.data.Recording
import com.paranormal.toolkit.ui.theme.AlertRed
import com.paranormal.toolkit.ui.theme.SpectralGreen
import com.paranormal.toolkit.ui.theme.WarnAmber
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

private val requiredPermissions: Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
fun RecorderScreen(contentPadding: PaddingValues, viewModel: RecorderViewModel = viewModel()) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            requiredPermissions.all {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> hasPermission = results.values.all { it } }

    val isRecording by viewModel.isRecording.collectAsState()
    val levelDb by viewModel.levelDb.collectAsState()
    val elapsedMillis by viewModel.elapsedMillis.collectAsState()
    val gain by viewModel.gain.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
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
            if (!hasPermission) {
                PermissionGate { permissionLauncher.launch(requiredPermissions) }
            }
            Text(
                "EVP Recorder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "AGC, Rauschunterdrückung und Echo-Cancelling sind deaktiviert - " +
                    "das Mikrofon nimmt so unverfälscht wie möglich auf.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )

            if (hasPermission) {
                LevelMeter(levelDb = levelDb, isRecording = isRecording)

                Spacer(Modifier.height(24.dp))

                Text(
                    formatElapsed(elapsedMillis),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isRecording) SpectralGreen else MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RecordButton(isRecording = isRecording, onClick = viewModel::toggleRecording)
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "Empfindlichkeit (Gain: ${String.format(Locale.GERMANY, "%.1fx", gain)})",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = gain,
                    onValueChange = viewModel::setGain,
                    valueRange = 1f..20f,
                    steps = 37,
                )
            }

            errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(message, color = AlertRed, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = viewModel::dismissError) { Text("OK") }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Aufnahmen (${recordings.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Audio laden")
                }
            }
        }

        if (recordings.isEmpty()) {
            Text(
                "Noch keine Aufnahmen. Starte oben eine EVP-Session oder lade eine Audiodatei " +
                    "aus dem Speicher - importierte Dateien lassen sich im Analyzer genauso " +
                    "bearbeiten.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recordings, key = { it.file.absolutePath }) { recording ->
                    RecordingRow(recording = recording, onDelete = { viewModel.deleteRecording(recording) })
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.MicOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Mikrofonzugriff wird benötigt",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Der EVP Recorder braucht Zugriff auf das Mikrofon, um Aufnahmen zu erstellen. " +
                "Vorhandene und importierte Dateien kannst du auch ohne die Berechtigung nutzen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        FloatingActionButton(onClick = onRequest) {
            Icon(Icons.Filled.Mic, contentDescription = "Berechtigung erteilen")
        }
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    val color = if (isRecording) AlertRed else SpectralGreen
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (isRecording) "Aufnahme stoppen" else "Aufnahme starten",
            tint = Color.Black,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun LevelMeter(levelDb: Float, isRecording: Boolean) {
    // -60..0 dBFS mapped to a 0..1 fill; below -60 dBFS reads as silence.
    val normalized = ((levelDb + 60f) / 60f).coerceIn(0f, 1f)
    val barColor = when {
        !isRecording -> MaterialTheme.colorScheme.surfaceVariant
        normalized > 0.9f -> AlertRed
        normalized > 0.6f -> WarnAmber
        else -> SpectralGreen
    }
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            drawRect(color = Color(0xFF1E1E29), size = Size(size.width, size.height))
            drawRect(color = barColor, size = Size(size.width * normalized, size.height))
        }
        Text(
            if (isRecording) "${levelDb.roundToInt()} dBFS" else "Bereit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RecordingRow(recording: Recording, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(recording.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${dateFormat.format(recording.timestampMillis)} · " +
                        "${formatElapsed(recording.durationMillis)} · " +
                        "${recording.sampleRateHz} Hz",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Löschen", tint = AlertRed)
            }
        }
    }
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.GERMANY, "%02d:%02d", minutes, seconds)
}
