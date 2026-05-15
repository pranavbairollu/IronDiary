package com.example.irondiary.ui.graph

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
    val rmsLevel by chatViewModel.rmsLevel.collectAsState()

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

        val personalRecords by chatViewModel.personalRecords.collectAsState()
        if (personalRecords.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = " HALL OF FAME",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    personalRecords.forEach { (exercise, record) ->
                        item {
                            val (weight, unit, date) = record
                            com.example.irondiary.ui.components.RecordTrophyCard(
                                exercise = exercise,
                                weight = weight,
                                unit = unit,
                                date = formatDisplayDate(date)
                            )
                        }
                    }
                }
            }
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
            rmsLevel = rmsLevel,
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

private fun formatDisplayDate(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    } catch (e: Exception) {
        dateStr
    }
}
