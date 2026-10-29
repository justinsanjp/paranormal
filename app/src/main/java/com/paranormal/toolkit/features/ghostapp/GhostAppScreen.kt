package com.paranormal.toolkit.features.ghostapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paranormal.toolkit.data.Language
import com.paranormal.toolkit.ui.PlaceholderScreen
import com.paranormal.toolkit.ui.theme.AlertRed
import com.paranormal.toolkit.ui.theme.SpectralGreen
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun GhostAppScreen(contentPadding: PaddingValues, viewModel: GhostAppViewModel = viewModel()) {
    if (!viewModel.hasSensors) {
        PlaceholderScreen(
            title = "GhostApp",
            description = "Dieses Gerät hat weder Beschleunigungs- noch Magnetfeldsensor - " +
                "die GhostApp braucht mindestens einen davon als Trigger-Quelle.",
            contentPadding = contentPadding,
        )
        return
    }

    val isRunning by viewModel.isRunning.collectAsState()
    val language by viewModel.language.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()
    val history by viewModel.history.collectAsState()
    val customWords by viewModel.customWords.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importWordDatabase) }

    // The session runs in a foreground service, which wants to show an
    // ongoing notification. Denying it doesn't stop the session, it only
    // hides that notification - so the session starts either way.
    val context = LocalContext.current
    var startAfterPermission by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (startAfterPermission) {
            startAfterPermission = false
            viewModel.toggleSession()
        }
    }

    val onToggleSession = {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (!isRunning && needsNotificationPermission) {
            startAfterPermission = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.toggleSession()
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.GERMANY) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(24.dp),
    ) {
        item {
            Text("GhostApp", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Die Sensoren entscheiden nur, WANN ein Wort fällt - sie lösen die " +
                    "Wortdatenbank aus, nie ein bestimmtes Wort. WELCHER Eintrag kommt, " +
                    "zieht ein Zufallsgenerator, der keine Sensordaten sieht. Ein " +
                    "Sensorwert kann damit kein bestimmtes Wort hervorrufen.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
        }

        errorMessage?.let { message ->
            item {
                Text(message, color = AlertRed, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = viewModel::dismissError) { Text("OK") }
            }
        }

        item {
            WordDatabaseCard(
                customWords = customWords,
                language = language,
                isImporting = isImporting,
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onClear = viewModel::clearWordDatabase,
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            Text("Sprache", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = language == Language.DE,
                    onClick = { viewModel.setLanguage(Language.DE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Deutsch") }
                SegmentedButton(
                    selected = language == Language.EN,
                    onClick = { viewModel.setLanguage(Language.EN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("English") }
            }

            Spacer(Modifier.height(16.dp))
            Text("Empfindlichkeit", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Slider(value = sensitivity, onValueChange = viewModel::setSensitivity, valueRange = 0f..1f)

            Spacer(Modifier.height(8.dp))
            Button(onClick = onToggleSession, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "Session stoppen" else "Session starten")
            }
            if (isRunning) {
                Text(
                    "Läuft weiter, wenn der Bildschirm ausgeht.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Verlauf (${history.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearHistory) { Text("Leeren") }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    if (isRunning) "Warte auf ersten Trigger…" else "Noch keine Session gestartet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            items(history.asReversed()) { spoken ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        spoken.text,
                        color = SpectralGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        timeFormat.format(spoken.timestampMillis),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordDatabaseCard(
    customWords: WordBankState,
    language: Language,
    isImporting: Boolean,
    onImport: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Wortdatenbank", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

            if (customWords.isLoaded) {
                Text(
                    "Eigene Liste: ${customWords.entries.size} Einträge",
                    color = SpectralGreen,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                customWords.sourceName?.let { source ->
                    Text(
                        "Quelle: $source",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val builtIn = WordBank.forLanguage(language)
                Text(
                    "Eingebaut: ${builtIn.size} Wörter " +
                        if (language == Language.DE) "(Deutsch)" else "(English)",
                    color = SpectralGreen,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Optional kannst du stattdessen eine eigene Textdatei laden - ein " +
                        "Eintrag pro Zeile, Leerzeilen und Zeilen mit # werden übersprungen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            if (isImporting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Importiere…", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImport) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (customWords.isLoaded) "Andere Liste" else "Eigene Liste laden")
                    }
                    if (customWords.isLoaded) {
                        TextButton(onClick = onClear) { Text("Eingebaute nutzen") }
                    }
                }
            }
        }
    }
}
