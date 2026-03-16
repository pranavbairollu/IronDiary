package com.example.irondiary.ui.academics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun LogStudySessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Float) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    val isDurationError = remember(duration) { duration.toFloatOrNull() == null && duration.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Study Session") },
        text = {
            Column {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration (hours)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isDurationError,
                    supportingText = { if (isDurationError) Text("Please enter a valid number") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    duration.toFloatOrNull()?.let {
                        onConfirm(subject, it)
                    }
                },
                enabled = subject.isNotBlank() && duration.toFloatOrNull() != null && (duration.toFloatOrNull() ?: 0f) > 0f
            ) {
                Text("Log")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
