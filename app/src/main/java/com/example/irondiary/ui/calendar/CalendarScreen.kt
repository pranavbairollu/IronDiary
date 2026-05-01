package com.example.irondiary.ui.calendar

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val selectedDate by mainViewModel.selectedDate.collectAsState()
    var showBottomSheet by remember { mutableStateOf(false) }
    
    val gymStreak by mainViewModel.gymStreak.collectAsState()
    val totalWorkouts by mainViewModel.totalWorkouts.collectAsState()
    val tasksResource by mainViewModel.tasks.collectAsState()
    val completedTasks by mainViewModel.selectedDateTasks.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe save status for error feedback
    val saveStatus by mainViewModel.saveStatus.collectAsState()
    LaunchedEffect(saveStatus) {
        if (saveStatus is Resource.Error) {
            snackbarHostState.showSnackbar((saveStatus as Resource.Error).message)
            mainViewModel.resetSaveStatus()
        }
    }

    val exportStatus by mainViewModel.exportStatus.collectAsState()
    LaunchedEffect(exportStatus) {
        when (exportStatus) {
            is Resource.Success -> {
                snackbarHostState.showSnackbar((exportStatus as Resource.Success).data)
                mainViewModel.resetExportStatus()
            }
            is Resource.Error -> {
                snackbarHostState.showSnackbar((exportStatus as Resource.Error).message)
                mainViewModel.resetExportStatus()
            }
            else -> {}
        }
    }

    val daysInMonth = remember(currentMonth) {
        val startDay = currentMonth.atDay(1)
        val endDay = currentMonth.atEndOfMonth()
        val firstDayOfWeek = startDay.dayOfWeek.value % 7 // 0 for Sunday
        
        List(firstDayOfWeek) { null } + (1..endDay.dayOfMonth).map { currentMonth.atDay(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IronDiary") },
                actions = {
                    if (exportStatus is Resource.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { mainViewModel.exportCalendarData(application) }) {
                            Icon(Icons.Default.Download, contentDescription = "Export to PDF")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            GymStreakHeader(streak = gymStreak, totalWorkouts = totalWorkouts)
            Spacer(modifier = Modifier.height(16.dp))
            
            CalendarHeader(
                currentMonth = currentMonth,
                onPreviousMonth = { 
                    currentMonth = currentMonth.minusMonths(1)
                    mainViewModel.onMonthChanged()
                },
                onNextMonth = { 
                    currentMonth = currentMonth.plusMonths(1)
                    mainViewModel.onMonthChanged()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            DayLabels()

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(300.dp) // Fixed height to leave room for the card
            ) {
                items(daysInMonth) { date ->
                    if (date != null) {
                        val dateId = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val logResource = dailyLogsResource
                        val log = if (logResource is Resource.Success) logResource.data[dateId] else null
                        val syncState = log?.syncState ?: com.example.irondiary.data.local.SyncState.SYNCED

                        CalendarDay(
                            date = date,
                            isSelected = date == selectedDate,
                            attendedGym = log?.attendedGym == true,
                            isRestDay = log?.isRestDay == true,
                            hasWeight = log?.weight != null,
                            syncState = syncState,
                            onDateClick = { mainViewModel.onDateSelected(date) },
                            onDateLongClick = { mainViewModel.toggleGymAttendance(date) }
                        )
                    } else {
                        Box(modifier = Modifier.aspectRatio(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedDate != null) {
                val dateId = selectedDate!!.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val log = when (val res = dailyLogsResource) {
                    is Resource.Success -> res.data[dateId] ?: DailyLog(date = dateId)
                    else -> DailyLog(date = dateId)
                }

                DailyInsightCard(
                    date = selectedDate!!,
                    log = log,
                    completedTasks = completedTasks,
                    isSaving = saveStatus is Resource.Loading,
                    onEditClick = { showBottomSheet = true }
                )
            } else {
                // Empty State / Placeholder
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a date to view insights",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
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
fun GymStreakHeader(streak: Int, totalWorkouts: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = Color(0xFFFF5722),
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "$streak Days",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Current Streak",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            VerticalDivider(modifier = Modifier.height(40.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "$totalWorkouts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Total Sessions",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    attendedGym: Boolean,
    isRestDay: Boolean,
    hasWeight: Boolean,
    syncState: com.example.irondiary.data.local.SyncState,
    onDateClick: () -> Unit,
    onDateLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val backgroundBrush = if (attendedGym) {
        Brush.radialGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                Color.Transparent
            )
        )
    } else null

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(CircleShape)
            .then(if (backgroundBrush != null) Modifier.background(backgroundBrush) else Modifier)
            .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) else Modifier)
            .combinedClickable(
                onClick = onDateClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDateLongClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Status Ring for Gym
        if (attendedGym) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFF4CAF50),
                    radius = size.minDimension / 2.2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        } else if (isRestDay) {
            // Indicator for Rest Day
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                Icon(
                    Icons.Default.BeachAccess,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(12.dp).padding(2.dp)
                )
            }
        }
        
        // Glow for Weight
        if (hasWeight) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                drawCircle(
                    color = Color(0xFF2196F3).copy(alpha = 0.3f),
                    radius = size.minDimension / 3f
                )
            }
        }

        Text(
            text = date.dayOfMonth.toString(),
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                date == LocalDate.now() -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (date == LocalDate.now() || isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DailyInsightCard(
    date: LocalDate,
    log: DailyLog,
    completedTasks: List<com.example.irondiary.data.model.Task>,
    isSaving: Boolean,
    onEditClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEEE")),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Sync Status Indicator
                        val syncIcon = when (log.syncState) {
                            com.example.irondiary.data.local.SyncState.SYNCED -> Icons.Default.CloudDone
                            com.example.irondiary.data.local.SyncState.PENDING -> Icons.Default.Sync
                            com.example.irondiary.data.local.SyncState.FAILED -> Icons.Default.CloudOff
                            com.example.irondiary.data.local.SyncState.DELETED -> Icons.Default.DeleteOutline
                        }
                        Icon(
                            syncIcon, 
                            contentDescription = null, 
                            modifier = Modifier.size(14.dp),
                            tint = if (log.syncState == com.example.irondiary.data.local.SyncState.FAILED) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("MMMM d")),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    FilledIconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Log")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                InsightItem(
                    icon = Icons.Default.FitnessCenter,
                    label = "Gym",
                    value = if (log.attendedGym) "Attended" else "Missed",
                    color = if (log.attendedGym) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
                InsightItem(
                    icon = Icons.Default.Scale,
                    label = "Weight",
                    value = log.weight?.let { "${it}kg" } ?: "--",
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
            }

            if (log.isRestDay) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BeachAccess,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Marked as Rest Day",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            if (completedTasks.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = "Completed Tasks",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(completedTasks) { task ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = task.description.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            
            if (!log.notes.isNullOrBlank()) {
                var isExpanded by remember { mutableStateOf(false) }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = log.notes!!,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = if (isExpanded) androidx.compose.ui.text.style.TextOverflow.Visible else androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.combinedClickable(
                        onClick = { isExpanded = !isExpanded },
                        onLongClick = {} // Avoid conflict with parent if any
                    )
                )
                if (log.notes!!.length > 100) {
                    Text(
                        text = if (isExpanded) "Show Less" else "Show More",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp).combinedClickable(onClick = { isExpanded = !isExpanded }, onLongClick = {})
                    )
                }
            }
        }
    }
}

@Composable
fun InsightItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    var isRestDay by remember { mutableStateOf(log.isRestDay) }
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
                Checkbox(checked = attendedGym, onCheckedChange = { 
                    attendedGym = it
                    if (it) isRestDay = false // Can't be both
                })
                Text("Attended Gym")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isRestDay, onCheckedChange = { 
                    isRestDay = it 
                    if (it) attendedGym = false // Can't be both
                })
                Text("Rest Day / Gym Closed")
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
                            isRestDay = isRestDay,
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
