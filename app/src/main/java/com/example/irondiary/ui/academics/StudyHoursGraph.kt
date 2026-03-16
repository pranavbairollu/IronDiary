package com.example.irondiary.ui.academics

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.example.irondiary.data.model.StudySession
import com.example.irondiary.data.model.Task
import com.example.irondiary.ui.graph.SimpleBarGraph
import com.example.irondiary.viewmodel.MainViewModel
import com.example.irondiary.viewmodel.MainViewModelFactory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun StudyHoursGraph() {
    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))
    val studySessionsResource by mainViewModel.studySessions.collectAsState()
    val tasksResource by mainViewModel.tasks.collectAsState()
    var selectedFilter by remember { mutableStateOf(FilterType.ALL) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Study Hours", style = MaterialTheme.typography.headlineMedium)
            FilterButtons(selected = selectedFilter, onFilterSelected = { selectedFilter = it })
        }
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            when (studySessionsResource) {
                is Resource.Loading -> {
                    CircularProgressIndicator()
                }
                is Resource.Error -> {
                    val errorMessage = (studySessionsResource as Resource.Error).message
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }
                is Resource.Success -> {
                    val allSessions = (studySessionsResource as Resource.Success).data
                    val filteredSessions = remember(allSessions, selectedFilter) {
                        filterSessions(allSessions, selectedFilter)
                    }

                    if (filteredSessions.isEmpty()) {
                        EmptyState()
                    } else {
                        val data = remember(filteredSessions) {
                            processSessionsForGraph(filteredSessions)
                        }
                        SimpleBarGraph(
                            modifier = Modifier.fillMaxSize(),
                            data = data,
                            barColor = MaterialTheme.colorScheme.tertiary,
                            onBarClick = { date ->
                                selectedDate = date
                            }
                        )
                    }

                    selectedDate?.let { date ->
                        val tasks = (tasksResource as? Resource.Success)?.data ?: emptyList()
                        val tasksForDate = tasks.filter { task ->
                            task.completedDate?.let {
                                val completedDate = Instant.ofEpochSecond(it.seconds).atZone(ZoneId.systemDefault()).toLocalDate()
                                completedDate == date
                            } ?: false
                        }
                        TasksForDayDialog(date, tasksForDate) {
                            selectedDate = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksForDayDialog(date: LocalDate, tasks: List<Task>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Completed Tasks for ${date.format(DateTimeFormatter.ofPattern("MMM d"))}") },
        text = {
            if (tasks.isNotEmpty()) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    tasks.forEach { task ->
                        Text(task.description)
                    }
                }
            } else {
                Text("No completed tasks for this day.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ready to Crush Your Goals?",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Log your study sessions to visualize your progress and build a strong academic foundation.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

enum class FilterType { WEEK, MONTH, ALL }

@Composable
private fun FilterButtons(selected: FilterType, onFilterSelected: (FilterType) -> Unit) {
    Row {
        FilterChip(selected = selected == FilterType.WEEK, onClick = { onFilterSelected(FilterType.WEEK) }, label = { Text("Week") })
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        FilterChip(selected = selected == FilterType.MONTH, onClick = { onFilterSelected(FilterType.MONTH) }, label = { Text("Month") })
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        FilterChip(selected = selected == FilterType.ALL, onClick = { onFilterSelected(FilterType.ALL) }, label = { Text("All") })
    }
}

private fun filterSessions(sessions: List<StudySession>, filter: FilterType): List<StudySession> {
    val now = LocalDate.now()
    val zoneId = ZoneId.systemDefault()

    return when (filter) {
        FilterType.WEEK -> {
            val startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zoneId).toInstant()
            val endOfWeek = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(23, 59, 59).atZone(zoneId).toInstant()
            sessions.filter { 
                val sessionInstant = Instant.ofEpochSecond(it.date.seconds)
                !sessionInstant.isBefore(startOfWeek) && !sessionInstant.isAfter(endOfWeek)
            }
        }
        FilterType.MONTH -> {
            val startOfMonth = now.withDayOfMonth(1).atStartOfDay(zoneId).toInstant()
            val endOfMonth = now.with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59).atZone(zoneId).toInstant()
            sessions.filter { 
                val sessionInstant = Instant.ofEpochSecond(it.date.seconds)
                !sessionInstant.isBefore(startOfMonth) && !sessionInstant.isAfter(endOfMonth)
            }
        }
        FilterType.ALL -> sessions
    }
}

private fun processSessionsForGraph(sessions: List<StudySession>): Map<LocalDate, Float> {
    return sessions
        .groupBy { 
            val instant = Instant.ofEpochSecond(it.date.seconds)
            instant.atZone(ZoneId.systemDefault()).toLocalDate()
        }
        .mapValues { (_, sessionsOnDate) -> 
            sessionsOnDate.sumOf { it.duration.toDouble() }.toFloat()
        }
}
