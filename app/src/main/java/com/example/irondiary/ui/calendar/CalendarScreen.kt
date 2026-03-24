package com.example.irondiary.ui.calendar

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.Resource
import com.example.irondiary.ui.components.SyncIndicator
import com.example.irondiary.viewmodel.MainViewModel
import com.example.irondiary.viewmodel.MainViewModelFactory
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))
    val dailyLogsResource by mainViewModel.dailyLogs.collectAsState()
    
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe save status for error feedback
    val saveStatus by mainViewModel.saveStatus.collectAsState()
    LaunchedEffect(saveStatus) {
        if (saveStatus is Resource.Error) {
            snackbarHostState.showSnackbar((saveStatus as Resource.Error).message)
            mainViewModel.resetSaveStatus()
        }
    }

    val daysInMonth = remember(currentMonth) {
        val startDay = currentMonth.atDay(1)
        val endDay = currentMonth.atEndOfMonth()
        val firstDayOfWeek = startDay.dayOfWeek.value % 7 // 0 for Sunday
        
        List(firstDayOfWeek) { null } + (1..endDay.dayOfMonth).map { currentMonth.atDay(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            CalendarHeader(
                currentMonth = currentMonth,
                onPreviousMonth = { currentMonth = currentMonth.minusMonths(1) },
                onNextMonth = { currentMonth = currentMonth.plusMonths(1) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            DayLabels()

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.weight(1f)
            ) {
                items(daysInMonth) { date ->
                    if (date != null) {
                        val dateId = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val log = when (val res = dailyLogsResource) {
                            is Resource.Success -> res.data[dateId]
                            else -> null
                        }
                        val syncState = log?.syncState ?: com.example.irondiary.data.local.SyncState.SYNCED

                        CalendarDay(
                            date = date,
                            isSelected = date == selectedDate,
                            hasLog = log != null,
                            syncState = syncState,
                            onDateClick = {
                                selectedDate = date
                                showBottomSheet = true
                            }
                        )
                    } else {
                        Box(modifier = Modifier.aspectRatio(1f))
                    }
                }
            }

            if (showBottomSheet && selectedDate != null) {
                val dateId = selectedDate!!.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val log = when (val res = dailyLogsResource) {
                    is Resource.Success -> res.data[dateId] ?: DailyLog(date = dateId)
                    else -> DailyLog(date = dateId)
                }

                DailyLogBottomSheet(
                    log = log,
                    saveStatus = saveStatus,
                    onDismiss = { showBottomSheet = false },
                    onSave = { updatedLog ->
                        mainViewModel.saveDailyLog(updatedLog)
                        showBottomSheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun CalendarHeader(
    currentMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
        }
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
        }
    }
}

@Composable
fun DayLabels() {
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    hasLog: Boolean,
    syncState: com.example.irondiary.data.local.SyncState,
    onDateClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        hasLog -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        date == LocalDate.now() -> MaterialTheme.colorScheme.primary
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                color = textColor,
                fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal
            )
            if (hasLog && syncState != com.example.irondiary.data.local.SyncState.SYNCED) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (syncState == com.example.irondiary.data.local.SyncState.FAILED) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.outline
                        )
                )
            } else if (hasLog) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLogBottomSheet(
    log: DailyLog,
    saveStatus: Resource<Unit>?,
    onDismiss: () -> Unit,
    onSave: (DailyLog) -> Unit
) {
    var attendedGym by remember { mutableStateOf(log.attendedGym) }
    var weight by remember { mutableStateOf(log.weight?.toString() ?: "") }
    var notes by remember { mutableStateOf(log.notes ?: "") }
    
    val isSaving = saveStatus is Resource.Loading
    
    val isWeightValid = remember(weight) {
        if (weight.isEmpty()) true
        else weight.toFloatOrNull()?.let { it > 0 && it <= 500 } ?: false
    }
    
    val isNotesValid = notes.length <= 2000

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Log for ${LocalDate.parse(log.date).format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = attendedGym, onCheckedChange = { attendedGym = it })
                Text("Attended Gym")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = weight,
                onValueChange = { if (it.length <= 10) weight = it },
                label = { Text("Weight (kg)") },
                modifier = Modifier.fillMaxWidth(),
                isError = !isWeightValid,
                supportingText = {
                    if (!isWeightValid) {
                        Text("Please enter a valid weight (0-500kg)")
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 2100) notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                isError = !isNotesValid,
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("${notes.length}/2000", color = if (isNotesValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isWeightValid && isNotesValid) {
                        onSave(log.copy(
                            attendedGym = attendedGym,
                            weight = weight.toFloatOrNull(),
                            notes = notes.trim().takeIf { it.isNotBlank() }
                        ))
                    }
                },
                enabled = isWeightValid && isNotesValid && !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Log")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
