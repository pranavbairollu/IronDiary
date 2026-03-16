package com.example.irondiary.ui.academics

import android.app.Application
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CompletedTasksList() {
    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))
    val tasksResource by mainViewModel.tasks.collectAsState()
    var sortOrder by remember { mutableStateOf(SortOrder.DESCENDING) }

    when (tasksResource) {
        is Resource.Loading -> {
            CircularProgressIndicator()
        }
        is Resource.Error -> {
            val errorMessage = (tasksResource as Resource.Error).message
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
        is Resource.Success -> {
            val tasks = (tasksResource as Resource.Success).data.filter { it.completed }

            if (tasks.isEmpty()) {
                EmptyState()
            } else {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sort by Date:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        SortMenu(selected = sortOrder, onSortSelected = { sortOrder = it })
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val sortedTasks = remember(tasks, sortOrder) {
                        sortTasks(tasks, sortOrder)
                    }
                    GroupedTaskList(
                        tasks = sortedTasks,
                        onTaskToggled = { mainViewModel.toggleTaskCompletion(it) },
                        onDeleteTask = { mainViewModel.deleteTask(it) }
                    )
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
        Icon(Icons.Filled.Done, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tasks completed... yet!",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Log your completed tasks to build momentum and see your progress over time.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
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
fun GroupedTaskList(tasks: List<Task>, onTaskToggled: (Task) -> Unit, onDeleteTask: (Task) -> Unit) {
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
                TaskItem(task = task, onTaskToggled = onTaskToggled, onDeleteTask = onDeleteTask)
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
fun TaskItem(task: Task, onTaskToggled: (Task) -> Unit, onDeleteTask: (Task) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isExpanded) 8.dp else 2.dp, label = "elevation")

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Task?") },
            text = { Text("Are you sure you want to delete this task?") },
            confirmButton = {
                Button(onClick = { 
                    onDeleteTask(task)
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        onClick = { isExpanded = !isExpanded }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onTaskToggled(task) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                Text(text = task.description, style = MaterialTheme.typography.bodyLarge)
                if (isExpanded) {
                    val completedDate = task.completedDate?.let {
                        Instant.ofEpochSecond(it.seconds)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    } ?: "N/A"
                    Text(text = "Completed on: $completedDate", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
