package com.example.irondiary.ui.academics

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.irondiary.data.Resource
import com.example.irondiary.data.model.Task
import com.example.irondiary.viewmodel.MainViewModel
import com.example.irondiary.viewmodel.MainViewModelFactory

@Composable
fun PendingTasksList() {
    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))
    val tasksResource by mainViewModel.tasks.collectAsState()
    val saveStatus by mainViewModel.saveStatus.collectAsState()
    val isLoading = saveStatus is Resource.Loading
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) showDeleteAllDialog = false },
            title = { Text("Delete All Tasks?") },
            text = { Text("This will permanently delete ALL tasks (both pending and completed). This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { mainViewModel.deleteAllTasks() },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("Delete All")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }, enabled = !isLoading) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset dialog state when operation completes successfully
    LaunchedEffect(saveStatus) {
        if (saveStatus is Resource.Success) {
            showDeleteAllDialog = false
        }
    }

    when (tasksResource) {
        is Resource.Loading -> {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is Resource.Error -> {
            val errorMessage = (tasksResource as Resource.Error).message
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        is Resource.Success -> {
            val tasks = (tasksResource as Resource.Success).data
            val pendingTasks = tasks.filter { !it.completed }

            if (pendingTasks.isEmpty()) {
                EmptyState()
            } else {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { showDeleteAllDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete All")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pendingTasks.forEach { task ->
                            PendingTaskItem(
                                task = task, 
                                onTaskToggled = { mainViewModel.toggleTaskCompletion(it) },
                                onDeleteTask = { mainViewModel.deleteTask(it) },
                                isInteractionDisabled = isLoading
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No pending tasks!",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add a new task to get started.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PendingTaskItem(
    task: Task, 
    onTaskToggled: (Task) -> Unit, 
    onDeleteTask: (Task) -> Unit,
    isInteractionDisabled: Boolean = false
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isInteractionDisabled) showDeleteDialog = false },
            title = { Text("Delete Task?") },
            text = { Text("Are you sure you want to delete this task?") },
            confirmButton = {
                Button(
                    onClick = { onDeleteTask(task) },
                    enabled = !isInteractionDisabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, enabled = !isInteractionDisabled) {
                    Text("Cancel")
                }
            }
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onTaskToggled(task) },
                enabled = !isInteractionDisabled
            )
            Text(text = task.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showDeleteDialog = true }, enabled = !isInteractionDisabled) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete task", tint = if (isInteractionDisabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error)
            }
        }
    }
}
