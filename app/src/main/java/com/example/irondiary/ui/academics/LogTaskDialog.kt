package com.example.irondiary.ui.academics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isLoading: Boolean = false
) {
    var description by remember { mutableStateOf("") }
    val trimmedDesc = description.trim()
    val isLengthTooLong = trimmedDesc.length > 500
    val isDescValid = trimmedDesc.isNotEmpty() && !isLengthTooLong

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a New Task") },
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmedDesc) },
                enabled = isDescValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Log")
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
