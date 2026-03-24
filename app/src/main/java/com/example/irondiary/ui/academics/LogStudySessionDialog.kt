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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme

@Composable
fun LogStudySessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Float) -> Unit,
    isLoading: Boolean = false
) {
    var subject by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    val isDurationInvalid = remember(duration) { 
        if (duration.isEmpty()) false 
        else {
            val d = duration.toFloatOrNull()
            d == null || d.isNaN() || d.isInfinite() || d <= 0f || d > 24f
        }
    }
    val isSubjectValid = subject.trim().isNotEmpty() && subject.length <= 100

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Log Study Session") },
        text = {
            Column {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { if (it.length <= 100) subject = it },
                    label = { Text("Subject") },
                    supportingText = { Text("${subject.length}/100") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration (hours)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isDurationInvalid,
                    supportingText = { if (isDurationInvalid) Text("Must be between 0 and 24") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    duration.toFloatOrNull()?.let {
                        if (!it.isNaN() && !it.isInfinite()) {
                            onConfirm(subject.trim(), it)
                        }
                    }
                },
                enabled = isSubjectValid && !isDurationInvalid && duration.isNotEmpty() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Log")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
