package com.example.irondiary.ui.academics

import android.app.Application
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.irondiary.data.Resource
import com.example.irondiary.data.model.Task
import com.example.irondiary.ui.components.EmptyState
import com.example.irondiary.ui.components.LoadingState
import com.example.irondiary.ui.components.SyncIndicator
import com.example.irondiary.viewmodel.MainViewModel
import com.example.irondiary.viewmodel.MainViewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CompletedTasksList() {
    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))
    val tasksResource by mainViewModel.tasks.collectAsState()
    val saveStatus by mainViewModel.saveStatus.collectAsState()
    val isLoading = saveStatus is Resource.Loading
    var sortOrder by remember { mutableStateOf(SortOrder.DESCENDING) }
    var showClearCompletedDialog by remember { mutableStateOf(false) }

    if (showClearCompletedDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) showClearCompletedDialog = false },
            title = { Text("Clear Completed Tasks?") },
            text = { Text("Are you sure you want to permanently delete all completed tasks?") },
            confirmButton = {
                Button(
                    onClick = { mainViewModel.clearCompletedTasks() },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("Clear")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCompletedDialog = false }, enabled = !isLoading) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(saveStatus) {
        if (saveStatus is Resource.Success) {
            showClearCompletedDialog = false
        }
    }

    when (tasksResource) {
        is Resource.Loading -> {
            LoadingState()
        }
        is Resource.Error -> {
            val errorMessage = (tasksResource as Resource.Error).message
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        is Resource.Success -> {
            val tasks = (tasksResource as Resource.Success).data.filter { it.completed }

            if (tasks.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Done,
                    title = "No tasks completed... yet!",
                    subtitle = "Log your completed tasks to build momentum and see your progress over time."
                )
            } else {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showClearCompletedDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            enabled = !isLoading
                        ) {
                            Text("Clear Completed")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sort:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            SortMenu(selected = sortOrder, onSortSelected = { sortOrder = it })
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val sortedTasks = remember(tasks, sortOrder) {
                        sortTasks(tasks, sortOrder)
                    }
                    GroupedTaskList(
                        tasks = sortedTasks,
                        onTaskToggled = { mainViewModel.toggleTaskCompletion(it) },
                        onDeleteTask = { mainViewModel.deleteTask(it) },
                        onEditTask = { t, desc -> mainViewModel.updateTaskDescription(t, desc) },
                        isInteractionDisabled = isLoading
                    )
                }
            }
        }
    }
}

enum class SortOrder { ASCENDING, DESCENDING }

@Composable
private fun SortMenu(selected: SortOrder, onSortSelected: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(if (selected == SortOrder.DESCENDING) "Newest First" else "Oldest First")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Newest First") }, onClick = { onSortSelected(SortOrder.DESCENDING); expanded = false })
            DropdownMenuItem(text = { Text("Oldest First") }, onClick = { onSortSelected(SortOrder.ASCENDING); expanded = false })
        }
    }
}

private fun sortTasks(tasks: List<Task>, order: SortOrder): List<Task> {
    return when (order) {
        SortOrder.ASCENDING -> tasks.sortedBy { it.completedDate?.seconds ?: Long.MAX_VALUE }
        SortOrder.DESCENDING -> tasks.sortedByDescending { it.completedDate?.seconds ?: Long.MIN_VALUE }
    }
}

@Composable
fun GroupedTaskList(
    tasks: List<Task>, 
    onTaskToggled: (Task) -> Unit, 
    onDeleteTask: (Task) -> Unit,
    onEditTask: (Task, String) -> Unit,
    isInteractionDisabled: Boolean = false
) {
    val groupedTasks = remember(tasks) {
        tasks.groupBy {
            it.completedDate?.let { completedDate ->
                Instant.ofEpochSecond(completedDate.seconds).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groupedTasks.forEach { (date, tasksOnDate) ->
            Text(
                text = date?.let { formatDateHeader(it) } ?: "Completed (Date Unknown)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            tasksOnDate.forEach { task ->
                TaskItem(
                    task = task, 
                    onTaskToggled = onTaskToggled, 
                    onDeleteTask = onDeleteTask,
                    onEditTask = onEditTask,
                    isInteractionDisabled = isInteractionDisabled
                )
            }
        }
    }
}

private fun formatDateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    return when {
        date.isEqual(today) -> "Today"
        date.isEqual(yesterday) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    }
}

@Composable
fun TaskItem(
    task: Task, 
    onTaskToggled: (Task) -> Unit, 
    onDeleteTask: (Task) -> Unit,
    onEditTask: (Task, String) -> Unit,
    isInteractionDisabled: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isExpanded) 8.dp else 2.dp, label = "elevation")

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

    if (showEditDialog) {
        TaskDialog(
            task = task,
            onDismiss = { showEditDialog = false },
            onConfirm = { newDesc ->
                onEditTask(task, newDesc)
                showEditDialog = false
            },
            isLoading = isInteractionDisabled
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        onClick = { if (!isInteractionDisabled) isExpanded = !isExpanded }
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
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                Text(text = task.description, style = MaterialTheme.typography.bodyLarge)
                SyncIndicator(syncState = task.syncState)
                if (isExpanded) {
                    val completedDate = task.completedDate?.let {
                        Instant.ofEpochSecond(it.seconds)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    } ?: "N/A"
                    Text(text = "Completed on: $completedDate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showEditDialog = true }, enabled = !isInteractionDisabled) {
                Icon(Icons.Default.Edit, contentDescription = "Edit task", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showDeleteDialog = true }, enabled = !isInteractionDisabled) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete task", tint = if (isInteractionDisabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error)
            }
        }
    }
}
