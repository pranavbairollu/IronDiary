package com.example.irondiary.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.Resource
import com.example.irondiary.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    val mainViewModel: MainViewModel = viewModel()
    val dailyLogsResource by mainViewModel.dailyLogs.collectAsState()
    val saveStatus by mainViewModel.saveStatus.collectAsState()
    
    LaunchedEffect(Unit) {
        mainViewModel.fetchDailyLogs()
    }

    LaunchedEffect(saveStatus) {
        if (saveStatus is Resource.Success || saveStatus is Resource.Error) {
            mainViewModel.resetSaveStatus()
        }
    }

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    if (showBottomSheet && selectedDate != null) {
        val dateId = selectedDate!!.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val log = (dailyLogsResource as? Resource.Success)?.data?.get(dateId) ?: DailyLog(date = dateId)

        DailyLogBottomSheet(
            log = log,
            onDismiss = { showBottomSheet = false },
            onSave = { updatedLog ->
                mainViewModel.saveDailyLog(dateId, updatedLog)
                showBottomSheet = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthHeader(currentMonth = currentMonth, onMonthChanged = { currentMonth = it })

        Box(modifier = Modifier.weight(1f)) {
            when (dailyLogsResource) {
                is Resource.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is Resource.Error -> {
                    val message = (dailyLogsResource as Resource.Error).message
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is Resource.Success -> {
                    val dailyLogs = (dailyLogsResource as Resource.Success).data
                    CalendarGrid(
                        currentMonth = currentMonth,
                        dailyLogs = dailyLogs,
                        onDateClick = { date ->
                            selectedDate = date
                            showBottomSheet = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MonthHeader(currentMonth: YearMonth, onMonthChanged: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { onMonthChanged(currentMonth.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
        }
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.titleLarge
        )
        IconButton(onClick = { onMonthChanged(currentMonth.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
        }
    }
}

@Composable
fun CalendarGrid(currentMonth: YearMonth, dailyLogs: Map<String, DailyLog>, onDateClick: (LocalDate) -> Unit) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek
    val emptyDays = (firstDayOfMonth.value - DayOfWeek.MONDAY.value + 7) % 7

    val days = (1..daysInMonth).map { currentMonth.atDay(it) }
    val dayHeaders = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            dayHeaders.forEach {
                Text(
                    text = it,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
        ) {
            items(count = emptyDays) {
                Box(Modifier.aspectRatio(1f))
            }
            items(days) { date ->
                val dateId = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val attendedGym = dailyLogs[dateId]?.attendedGym == true

                DayCell(
                    date = date,
                    isGymAttended = attendedGym,
                    onDateClick = { onDateClick(date) }
                )
            }
        }
    }
}

@Composable
fun DayCell(date: LocalDate, isGymAttended: Boolean, onDateClick: () -> Unit) {
    val isToday = date == LocalDate.now()
    val backgroundColor = when {
        isGymAttended -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val textColor = when {
        isGymAttended -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onDateClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = textColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLogBottomSheet(log: DailyLog, onDismiss: () -> Unit, onSave: (DailyLog) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var attendedGym by remember(log) { mutableStateOf(log.attendedGym) }
    var weight by remember(log) { mutableStateOf(log.weight?.toString() ?: "") }
    var notes by remember(log) { mutableStateOf(log.notes ?: "") }

    val isWeightInvalid = remember(weight) { 
        if (weight.isEmpty()) false 
        else {
            val w = weight.toFloatOrNull()
            w == null || w <= 1.0f || w >= 500.0f
        }
    }

    ModalBottomSheet(sheetState = sheetState, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Log for ${log.date}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Went to Gym", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = attendedGym, onCheckedChange = { attendedGym = it })
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text("Weight (kg)") },
                modifier = Modifier.fillMaxWidth(),
                isError = isWeightInvalid,
                supportingText = {
                    if (isWeightInvalid) {
                        Text("Weight must be between 1.0 and 500.0 kg")
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (!isWeightInvalid) {
                        onSave(log.copy(attendedGym = attendedGym, weight = weight.toFloatOrNull(), notes = notes))
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                },
                enabled = !isWeightInvalid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
