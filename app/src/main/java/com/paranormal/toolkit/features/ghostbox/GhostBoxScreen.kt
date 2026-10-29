package com.paranormal.toolkit.features.ghostbox

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paranormal.toolkit.ui.theme.AlertRed
import com.paranormal.toolkit.ui.theme.SpectralGreen
import com.paranormal.toolkit.ui.theme.WarnAmber
import java.util.Locale

@Composable
fun GhostBoxScreen(contentPadding: PaddingValues, viewModel: GhostBoxViewModel = viewModel()) {
    val backend by viewModel.backend.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
    ) {
        Text("GhostBox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Immer echte Hardware, nie ein simuliertes Signal - entweder über einen " +
                "USB-Radio-Dongle oder über den eingebauten UKW-Chip via einer kompatiblen " +
                "Begleit-App.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = backend == RadioBackend.USB_SDR,
                onClick = { viewModel.setBackend(RadioBackend.USB_SDR) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("USB-SDR") }
            SegmentedButton(
                selected = backend == RadioBackend.COMPANION_APP,
                onClick = { viewModel.setBackend(RadioBackend.COMPANION_APP) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Interner UKW-Chip") }
        }

        Spacer(Modifier.height(20.dp))

        when (backend) {
            RadioBackend.USB_SDR -> UsbSdrPanel(viewModel)
            RadioBackend.COMPANION_APP -> CompanionAppPanel(viewModel)
        }
    }
}

@Composable
private fun UsbSdrPanel(viewModel: GhostBoxViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val frequencyHz by viewModel.frequencyHz.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    ConnectionStatusCard(connectionState, onRequestPermission = viewModel::requestPermission)

    if (connectionState is UsbConnectionState.Ready) {
        Spacer(Modifier.height(20.dp))

        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = mode == GhostBoxMode.SCAN,
                onClick = { viewModel.setMode(GhostBoxMode.SCAN) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Scan") }
            SegmentedButton(
                selected = mode == GhostBoxMode.MANUAL,
                onClick = { viewModel.setMode(GhostBoxMode.MANUAL) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Manuell") }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            String.format(Locale.GERMANY, "%.1f MHz", frequencyHz / 1_000_000f),
            style = MaterialTheme.typography.titleLarge,
            color = if (isListening) SpectralGreen else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (mode == GhostBoxMode.SCAN) {
                "Sweept den UKW-Bereich 87.5-108 MHz in 200-kHz-Schritten, wie eine echte Spirit Box."
            } else {
                "Feste Frequenz, dauerhaft demoduliert."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Button(onClick = viewModel::toggleListening) {
            Icon(
                imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isListening) "Stopp" else "Start")
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(errorMessage.orEmpty(), color = AlertRed, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CompanionAppPanel(viewModel: GhostBoxViewModel) {
    val isScanning by viewModel.isCompanionScanning.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Voraussetzungen", fontWeight = FontWeight.Bold)
            Text(
                "1. Eine FM-Radio-App, die den eingebauten UKW-Chip nutzt, muss installiert " +
                    "und geöffnet sein.\n" +
                    "2. UKW muss dort eingeschaltet sein (nicht nur die App offen).\n" +
                    "3. Kabelgebundene Kopfhörer müssen eingesteckt sein (Antenne).\n\n" +
                    "GhostBox sendet dann normale Medientasten-Befehle (Play/Pause, " +
                    "Weiter, Zurück) - denselben Signalweg, den auch Kopfhörer-Tasten " +
                    "nutzen. Ton kommt direkt aus dieser App/deinen Kopfhörern, nicht aus " +
                    "GhostBox selbst. Läuft keine passende App mit aktivem UKW, passiert " +
                    "schlicht nichts - kein simuliertes Ersatzsignal.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = viewModel::companionSeekPrevious) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Vorheriger Sender")
        }
        Button(onClick = viewModel::companionTogglePlayback) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
        }
        OutlinedButton(onClick = viewModel::companionSeekNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Nächster Sender")
        }
    }

    Spacer(Modifier.height(20.dp))
    Button(onClick = viewModel::toggleCompanionScan) {
        Icon(
            imageVector = if (isScanning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(if (isScanning) "Sweep stoppen" else "Automatischen Sweep starten")
    }
    if (isScanning) {
        Text(
            "Springt alle 3 Sekunden zum nächsten Sender - wie eine echte Spirit Box.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ConnectionStatusCard(state: UsbConnectionState, onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Usb, contentDescription = null, tint = statusColor(state))
                Spacer(Modifier.width(8.dp))
                Text(statusTitle(state), fontWeight = FontWeight.Bold, color = statusColor(state))
            }
            Text(
                statusDescription(state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (state is UsbConnectionState.PermissionRequired) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRequestPermission) { Text("Zugriff erlauben") }
            }
        }
    }
}

private fun statusTitle(state: UsbConnectionState): String = when (state) {
    is UsbConnectionState.NoDevice -> "Kein Gerät erkannt"
    is UsbConnectionState.PermissionRequired -> "Berechtigung erforderlich"
    is UsbConnectionState.Ready -> "Gerät bereit"
    is UsbConnectionState.Error -> "Fehler"
}

private fun statusDescription(state: UsbConnectionState): String = when (state) {
    is UsbConnectionState.NoDevice ->
        "Schließe einen RTL-SDR-USB-Dongle über ein USB-OTG-Kabel/Adapter an."
    is UsbConnectionState.PermissionRequired ->
        "Ein unterstütztes Gerät wurde gefunden (${state.device.deviceName}), " +
            "Android muss den Zugriff erst freigeben."
    is UsbConnectionState.Ready -> "${state.device.deviceName} verbunden."
    is UsbConnectionState.Error -> state.message
}

@Composable
private fun statusColor(state: UsbConnectionState) = when (state) {
    is UsbConnectionState.NoDevice -> MaterialTheme.colorScheme.onSurfaceVariant
    is UsbConnectionState.PermissionRequired -> WarnAmber
    is UsbConnectionState.Ready -> SpectralGreen
    is UsbConnectionState.Error -> AlertRed
}
