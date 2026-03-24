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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.irondiary.ui.components.EmptyState
import com.example.irondiary.ui.components.LoadingState
import com.example.irondiary.ui.graph.SimpleBarGraph
import com.example.irondiary.viewmodel.MainViewModel
import com.example.irondiary.viewmodel.MainViewModelFactory
import java.util.*

@Composable
fun StudyHoursGraph() {
    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))
    val studySessionsResource by mainViewModel.studySessions.collectAsState()
    
    var selectedFilter by remember { mutableStateOf(StudyFilter.ALL) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Study Hours",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        FilterButtons(selectedFilter = selectedFilter, onFilterSelected = { selectedFilter = it })

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            val sessions = when (val res = studySessionsResource) {
                is Resource.Success -> res.data
                else -> emptyList()
            }

            if (studySessionsResource is Resource.Loading) {
                LoadingState()
            } else if (studySessionsResource is Resource.Error) {
                Text(text = (studySessionsResource as Resource.Error).message, color = MaterialTheme.colorScheme.error)
            } else if (sessions.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.School,
                    title = "Ready to Crush Your Goals?",
                    subtitle = "Log your study sessions to visualize your progress and build a strong academic foundation."
                )
            } else {
                val data = remember(sessions, selectedFilter) {
                    processSessionsForGraph(filterSessions(sessions, selectedFilter))
                }
                    SimpleBarGraph(
                        modifier = Modifier.fillMaxSize(),
                        data = data,
                        barColor = MaterialTheme.colorScheme.tertiary
                    )
            }
        }
    }
}

enum class StudyFilter { ALL, LAST_7_DAYS, THIS_MONTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterButtons(selectedFilter: StudyFilter, onFilterSelected: (StudyFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StudyFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

private fun filterSessions(sessions: List<StudySession>, filter: StudyFilter): List<StudySession> {
    return when (filter) {
        StudyFilter.ALL -> sessions
        StudyFilter.LAST_7_DAYS -> {
            val limitDate = java.time.LocalDate.now().minusDays(7)
            val limitMillis = limitDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            sessions.filter { it.date.toDate().time >= limitMillis }
        }
        StudyFilter.THIS_MONTH -> {
            val limitDate = java.time.LocalDate.now().withDayOfMonth(1)
            val limitMillis = limitDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            sessions.filter { it.date.toDate().time >= limitMillis }
        }
    }
}

private fun processSessionsForGraph(sessions: List<StudySession>): Map<java.time.LocalDate, Float> {
    return sessions.groupBy { session ->
        java.time.Instant.ofEpochSecond(session.date.seconds)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
    }.mapValues { (_, sessionsOnDate) ->
        sessionsOnDate.sumOf { it.duration }.toFloat()
    }.toSortedMap()
}
