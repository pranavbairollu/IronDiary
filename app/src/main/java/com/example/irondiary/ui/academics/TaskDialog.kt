package com.example.irondiary.ui.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar
import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.irondiary.data.model.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDialog(
    task: Task? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?) -> Unit,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    var description by remember { mutableStateOf(task?.description ?: "") }
    var reminderTime by remember { mutableStateOf(task?.reminderTime) }
    
    val calendar = remember { Calendar.getInstance() }
    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                reminderTime = newCalendar.timeInMillis
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        )
    }
    val trimmedDesc = description.trim()
    val isLengthTooLong = trimmedDesc.length > 500
    val isDescValid = trimmedDesc.isNotEmpty() && !isLengthTooLong
    val isEditing = task != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Task" else "Add a New Task") },
        text = {
            Column {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Task description") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = isLengthTooLong,
                    enabled = !isLoading,
                    placeholder = { Text("e.g., Complete physics homework") },
                    supportingText = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (isLengthTooLong) {
                                Text("Description is too long", color = MaterialTheme.colorScheme.error)
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Text("${trimmedDesc.length}/500")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.size(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (reminderTime != null) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = if (reminderTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (reminderTime != null) {
                                val cal = Calendar.getInstance().apply { timeInMillis = reminderTime!! }
                                val hour = cal.get(Calendar.HOUR_OF_DAY)
                                val minute = cal.get(Calendar.MINUTE)
                                String.format("Reminder: %02d:%02d", hour, minute)
                            } else {
                                "No Reminder Set"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    TextButton(onClick = { timePickerDialog.show() }) {
                        Text(if (reminderTime == null) "Set Reminder" else "Change")
                    }
                    
                    if (reminderTime != null) {
                        IconButton(onClick = { reminderTime = null }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove Reminder",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmedDesc, reminderTime) },
                enabled = isDescValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isEditing) "Save" else "Log")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}
