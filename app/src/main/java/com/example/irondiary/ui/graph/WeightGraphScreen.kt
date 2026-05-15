package com.example.irondiary.ui.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.Resource
import com.example.irondiary.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.LocalContext
import com.example.irondiary.data.repository.IronDiaryRepository
import com.example.irondiary.ui.components.ChatWindow
import com.example.irondiary.viewmodel.ChatViewModel
import com.example.irondiary.viewmodel.ChatViewModelFactory

@Composable
fun WeightGraphScreen() {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val repository = remember { IronDiaryRepository(application) }
    
    val mainViewModel: MainViewModel = viewModel(factory = com.example.irondiary.viewmodel.MainViewModelFactory(application))
    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(repository))
    
    val weightDataResource by mainViewModel.weightData.collectAsState()
    val chatMessages by chatViewModel.messages.collectAsState()
    val isTyping by chatViewModel.isTyping.collectAsState()
    val isListening by chatViewModel.isListening.collectAsState()

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            chatViewModel.toggleVoiceInput(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Weight Trend",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )
        
        val topInsight by chatViewModel.topInsight.collectAsState()
        topInsight?.let {
            com.example.irondiary.ui.components.IntelligenceCard(
                insight = it,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (weightDataResource) {
                    is Resource.Loading -> {
                        CircularProgressIndicator()
                    }
                    is Resource.Error -> {
                        val message = (weightDataResource as Resource.Error).message
                        Text(text = message, color = MaterialTheme.colorScheme.error)
                    }
                    is Resource.Success -> {
                        val weightData = (weightDataResource as Resource.Success).data
                        WeightGraph(weightData = weightData)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        ChatWindow(
            messages = chatMessages,
            isTyping = isTyping,
            isListening = isListening,
            onSendMessage = { chatViewModel.sendMessage(it) },
            onToggleVoice = {
                val permission = android.Manifest.permission.RECORD_AUDIO
                val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (isGranted) {
                    chatViewModel.toggleVoiceInput(context)
                } else {
                    permissionLauncher.launch(permission)
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun WeightGraph(weightData: List<DailyLog>) {
    val validWeightData = remember(weightData) {
        weightData
            .filter { it.weight != null && it.weight > 0 }
            .sortedBy { it.date }
    }

    if (validWeightData.size < 2) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Log your weight on multiple days to see your trend.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Progress visualization requires at least 2 entries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val weightValues = remember(validWeightData) {
            validWeightData.map { it.weight!!.toDouble() }
        }

        val dateLabels = remember(validWeightData) {
            val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
            val displayFormatter = DateTimeFormatter.ofPattern("MMM dd")
            validWeightData.map {
                try {
                    LocalDate.parse(it.date, isoFormatter).format(displayFormatter)
                } catch (e: Exception) {
                    it.date
                }
            }
        }

        val tooltipLabels = remember(validWeightData) {
            val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
            val fullFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
            validWeightData.map {
                try {
                    LocalDate.parse(it.date, isoFormatter).format(fullFormatter)
                } catch (e: Exception) {
                    it.date
                }
            }
        }

        SimpleLineGraph(
            dataPoints = weightValues,
            labels = dateLabels,
            modifier = Modifier.fillMaxSize(),
            tooltipFormatter = { value, label -> 
                val index = weightValues.indexOf(value)
                val fullDate = if (index != -1) tooltipLabels[index] else label
                "${String.format("%.1f", value)} kgs on $fullDate" 
            },
            lineColor = MaterialTheme.colorScheme.tertiary,
            fillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
            minYValue = 0.0
        )
    }
}
