package com.example.irondiary.ui.components

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.irondiary.util.NotificationHelper
import com.example.irondiary.viewmodel.MainViewModel

@Composable
fun SettingsDialog(
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var permissionDeniedAlert by remember { mutableStateOf(false) }

    if (permissionDeniedAlert) {
        AlertDialog(
            onDismissRequest = { permissionDeniedAlert = false },
            title = { Text("Permission Required") },
            text = { Text("Please enable notifications in your phone's Settings to use daily reminders.") },
            confirmButton = {
                TextButton(onClick = { permissionDeniedAlert = false }) {
                    Text("OK")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Daily reminders at 9:30 PM have been removed in favor of task-specific reminders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You can now set a custom reminder time for each task when you create or edit it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
