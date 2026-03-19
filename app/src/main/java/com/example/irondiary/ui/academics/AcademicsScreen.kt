package com.example.irondiary.ui.academics

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.irondiary.viewmodel.AuthViewModel
import com.example.irondiary.viewmodel.MainViewModel
import com.example.irondiary.viewmodel.MainViewModelFactory

@Composable
fun AcademicsScreen() {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Pending", "Completed")

    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))
    val authViewModel: AuthViewModel = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current



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
        }

        item {
            val sessionsResource by mainViewModel.studySessions.collectAsState()
            if (sessionsResource is Resource.Success) {
                val sessions = (sessionsResource as Resource.Success).data
                    .sortedByDescending { it.date.seconds }
                    .take(5)
                
                if (sessions.isNotEmpty()) {
                    Text(
                        "Recent Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
            }
        }

        val sessionsResource = mainViewModel.studySessions.value
        if (sessionsResource is Resource.Success) {
            val sessions = sessionsResource.data
                .sortedByDescending { it.date.seconds }
                .take(5)
            
            items(sessions, key = { it.docId }) { session ->
                StudySessionItem(session)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}


@Composable
fun StudySessionItem(session: com.example.irondiary.data.model.StudySession) {
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
            Text(
                "${session.duration} hrs",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

