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
    val isDailyReminderEnabled by mainViewModel.isDailyReminderEnabled.collectAsState()
    var permissionDeniedAlert by remember { mutableStateOf(false) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            mainViewModel.toggleDailyReminder(true, context)
        } else {
            // Permission denied, revert the toggle attempt
            permissionDeniedAlert = true
            mainViewModel.toggleDailyReminder(false, context)
        }
    }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Daily Reminders (9:30 PM)", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isDailyReminderEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (!NotificationHelper.hasNotificationPermission(context)) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        // SDK < 33, permission implicitly granted, but just in case:
                                        mainViewModel.toggleDailyReminder(true, context)
                                    }
                                } else {
                                    mainViewModel.toggleDailyReminder(true, context)
                                }
                            } else {
                                mainViewModel.toggleDailyReminder(false, context)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
