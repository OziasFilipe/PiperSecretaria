package com.piper.secretaria.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.piper.secretaria.update.UpdateInfo

/** Pop-up mostrado ao abrir o app quando existe versão nova. */
@Composable
fun UpdateDialog(info: UpdateInfo, currentVersion: String, onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = { FluteMascot(Modifier.size(72.dp), talking = true) },
        title = { Text("Nova versão da Piper!") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("A versão ${info.versionName} está disponível (você usa a $currentVersion).")
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(info.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text("Deseja atualizar agora?")
            }
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("Atualizar agora") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Depois") } },
    )
}

@Composable
fun DownloadProgress(progress: Float) {
    Column(Modifier.fillMaxWidth()) {
        Text("Baixando… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}
