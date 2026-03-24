package com.example.irondiary.ui.academics

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.irondiary.viewmodel.AuthViewModel
import com.example.irondiary.viewmodel.MainViewModel
import com.example.irondiary.viewmodel.MainViewModelFactory
import com.example.irondiary.data.Resource
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicsScreen() {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Pending", "Completed")

    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))
    val authViewModel: AuthViewModel = viewModel()
    val sessionsResource by mainViewModel.studySessions.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Academics Icon",
                        modifier = Modifier.size(32.dp)
                    )
                    Text("Academics", style = MaterialTheme.typography.headlineLarge)
                }
                IconButton(onClick = { authViewModel.signOut() }) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Logout"
                    )
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StudyHoursGraph()
                }
            }
        }

        item {
            if (sessionsResource is Resource.Success) {
                val sessions = (sessionsResource as Resource.Success).data
                StudyTrendAnalysis(sessions = sessions)
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        tabs.forEachIndexed { index, title ->
                            Tab(text = { Text(title) },
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index })
                        }
                    }
                    Column(modifier = Modifier.padding(16.dp)) {
                        when (selectedTabIndex) {
                            0 -> PendingTasksList()
                            1 -> CompletedTasksList()
                        }
                    }
                }
            }
        }

        item {
            if (sessionsResource is Resource.Success) {
                val sessions = (sessionsResource as Resource.Success).data
                if (sessions.isNotEmpty()) {
                    Text(
                        "Recent Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
            }
        }

        val sessionsResourceSnapshot = mainViewModel.studySessions.value
        if (sessionsResourceSnapshot is Resource.Success) {
            val sessions = sessionsResourceSnapshot.data
                .sortedByDescending { it.updatedAt }
                .take(5)
            
            items(sessions, key = { it.docId }) { session ->
                StudySessionItem(session, onDelete = { mainViewModel.deleteStudySession(session) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun StudySessionItem(
    session: com.example.irondiary.data.model.StudySession,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.subject, style = MaterialTheme.typography.titleMedium)
                com.example.irondiary.ui.components.SyncIndicator(syncState = session.syncState)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${session.duration} hrs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete session",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
