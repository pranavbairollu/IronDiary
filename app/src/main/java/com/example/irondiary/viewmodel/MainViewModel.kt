package com.example.irondiary.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.Resource
import com.example.irondiary.data.model.StudySession
import com.example.irondiary.data.model.Task
import com.google.firebase.Timestamp
import com.example.irondiary.data.repository.IronDiaryRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/**
 * The primary ViewModel for managing UI state and business logic in the IronDiary app.
 *
 * It acts as a bridge between the [IronDiaryRepository] and the Compose UI,
 * exposing data as reactive [StateFlow]s and handling user interactions.
 */
class MainViewModel(private val repository: IronDiaryRepository) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val sharedPreferences = repository.context.getSharedPreferences("IronDiaryPrefs", Context.MODE_PRIVATE)

    private val _dailyLogs = MutableStateFlow<Resource<Map<String, DailyLog>>>(Resource.Success(emptyMap()))
    val dailyLogs: StateFlow<Resource<Map<String, DailyLog>>> = _dailyLogs.asStateFlow()

    private val _weightData = MutableStateFlow<Resource<List<DailyLog>>>(Resource.Success(emptyList()))
    val weightData: StateFlow<Resource<List<DailyLog>>> = _weightData.asStateFlow()

    private val _studySessions = MutableStateFlow<Resource<List<StudySession>>>(Resource.Success(emptyList()))
    val studySessions: StateFlow<Resource<List<StudySession>>> = _studySessions.asStateFlow()

    private val _tasks = MutableStateFlow<Resource<List<Task>>>(Resource.Success(emptyList()))
    val tasks: StateFlow<Resource<List<Task>>> = _tasks.asStateFlow()

    private val _isDailyReminderEnabled = MutableStateFlow(
        sharedPreferences.getBoolean("daily_reminders_enabled", false) // Default to false
    )
    val isDailyReminderEnabled: StateFlow<Boolean> = _isDailyReminderEnabled.asStateFlow()

    private val _saveStatus = MutableStateFlow<Resource<Unit>?>(null)
    val saveStatus: StateFlow<Resource<Unit>?> = _saveStatus.asStateFlow()

    private var dailyLogsJob: Job? = null
    private var weightDataJob: Job? = null
    private var studySessionsJob: Job? = null
    private var tasksJob: Job? = null
    
    // Map to keep track of active toggle jobs per taskId to prevent rapid UI toggling floods
    private val activeToggleJobs = mutableMapOf<String, Job>()
    
    // Map to keep track of active save jobs per date to prevent redundant save floods
    private val activeSaveJobs = mutableMapOf<String, Job>()

    init {
        fetchDailyLogs()
        fetchWeightData()
        fetchStudySessions()
        fetchTasks()
        
        // Trigger background sync on app startup (Safeguard #4)
        repository.enqueueSync()
    }


    fun fetchDailyLogs() {
        dailyLogsJob?.cancel()
        dailyLogsJob = viewModelScope.launch {
            _dailyLogs.value = Resource.Loading
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _dailyLogs.value = Resource.Error("User not logged in.")
                return@launch
            }

            try {
                repository.getDailyLogs(userId).collect { logsMap ->
                    _dailyLogs.value = Resource.Success(logsMap)
                }
            } catch (e: Exception) {
                _dailyLogs.value = Resource.Error("Error loading logs: ${e.message}")
            }
        }
    }

    fun fetchWeightData() {
        weightDataJob?.cancel()
        weightDataJob = viewModelScope.launch {
            _weightData.value = Resource.Loading
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _weightData.value = Resource.Error("User not logged in.")
                return@launch
            }

            try {
                repository.getWeightData(userId).collect { data ->
                    _weightData.value = Resource.Success(data)
                }
            } catch (e: Exception) {
                _weightData.value = Resource.Error("Error loading weight data: ${e.message}")
            }
        }
    }

    fun fetchStudySessions() {
        studySessionsJob?.cancel()
        studySessionsJob = viewModelScope.launch {
            _studySessions.value = Resource.Loading
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _studySessions.value = Resource.Error("User not logged in.")
                return@launch
            }

            try {
                repository.getStudySessions(userId).collect { sessions ->
                    _studySessions.value = Resource.Success(sessions)
                }
            } catch (e: Exception) {
                _studySessions.value = Resource.Error("Error loading study sessions: ${e.message}")
            }
        }
    }

    fun fetchTasks() {
        tasksJob?.cancel()
        tasksJob = viewModelScope.launch {
            _tasks.value = Resource.Loading
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _tasks.value = Resource.Error("You must be logged in to see your data.")
                return@launch
            }

            try {
                repository.getTasks(userId).collect { taskList ->
                    _tasks.value = Resource.Success(taskList)
                }
            } catch (e: Exception) {
                _tasks.value = Resource.Error("Error loading tasks: ${e.message}")
            }
        }
    }

    fun saveDailyLog(log: DailyLog) {
        val userId = auth.currentUser?.uid ?: return
        
        // Validation: Ensure weight is within a realistic range and notes aren't too long
        val weight = log.weight
        if (weight != null && (weight <= 0 || weight > 500)) {
            _saveStatus.value = Resource.Error("Weight must be between 0 and 500 kg.")
            return
        }
        
        val notes = log.notes
        if (notes != null && notes.length > 2000) {
            _saveStatus.value = Resource.Error("Notes cannot exceed 2000 characters.")
            return
        }

        _saveStatus.value = Resource.Loading
        
        // Cancel any existing save job for this specific date
        activeSaveJobs[log.date]?.cancel()
        
        activeSaveJobs[log.date] = viewModelScope.launch {
            try {
                repository.saveDailyLog(log, userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _saveStatus.value = Resource.Error("Failed to save daily log: ${e.message}")
                }
            } finally {
                activeSaveJobs.remove(log.date)
            }
        }
    }

    fun addStudySession(subject: String, duration: Float) {
        val trimmedSubject = subject.trim()
        if (trimmedSubject.isEmpty()) {
            _saveStatus.value = Resource.Error("Subject cannot be empty.")
            return
        }
        if (trimmedSubject.length > 100) {
            _saveStatus.value = Resource.Error("Subject cannot exceed 100 characters.")
            return
        }
        if (duration.isNaN() || duration.isInfinite() || duration <= 0f || duration > 24f) {
            _saveStatus.value = Resource.Error("Duration must be between 0 and 24 hours.")
            return
        }

        val userId = auth.currentUser?.uid ?: return
        _saveStatus.value = Resource.Loading
        
        activeSaveJobs["study_session_save"]?.cancel()
        
        activeSaveJobs["study_session_save"] = viewModelScope.launch {
            try {
                val session = StudySession(subject = trimmedSubject, date = com.google.firebase.Timestamp(Date()), duration = duration.toDouble())
                repository.addStudySession(session, userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _saveStatus.value = Resource.Error("Failed to add study session: ${e.message}")
                }
            } finally {
                activeSaveJobs.remove("study_session_save")
            }
        }
    }

    fun deleteStudySession(session: StudySession) {
        val docId = session.docId
        if (docId.isEmpty()) {
            Log.e("MainViewModel", "Cannot delete study session with empty docId: ${session.subject}")
            _saveStatus.value = Resource.Error("Logic Error: Missing Session ID. Please refresh.")
            return
        }

        val userId = auth.currentUser?.uid ?: return
        _saveStatus.value = Resource.Loading
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Initiating deletion of session $docId for user $userId")
                repository.deleteStudySession(docId, userId)
                _saveStatus.value = Resource.Success(Unit)
                Log.d("MainViewModel", "Successfully marked session $docId as deleted")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete study session $docId", e)
                _saveStatus.value = Resource.Error("Failed to delete study session: ${e.message}")
            }
        }
    }

    fun addTask(description: String) {
        val trimmedDesc = description.trim()
        if (trimmedDesc.isEmpty()) {
            _saveStatus.value = Resource.Error("Task description cannot be empty.")
            return
        }
        if (trimmedDesc.length > 500) {
            _saveStatus.value = Resource.Error("Task description is too long.")
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            _saveStatus.value = Resource.Error("Must be logged in.")
            return
        }

        _saveStatus.value = Resource.Loading
        viewModelScope.launch {
            try {
                repository.addTask(userId, trimmedDesc)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to save task: ${e.message}")
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        val userId = auth.currentUser?.uid ?: return
        val docId = task.docId
        if (docId.isEmpty()) return
        
        // Cancel any existing toggle job for this specific task
        activeToggleJobs[docId]?.cancel()
        
        // Debounce: Wait 300ms before pushing to repository
        activeToggleJobs[docId] = viewModelScope.launch {
            try {
                delay(300)
                
                val newCompletedStatus = !task.completed
                val updatedTask = task.copy(
                    completed = newCompletedStatus,
                    completedDate = if (newCompletedStatus) com.google.firebase.Timestamp.now() else null
                )
                repository.updateTask(updatedTask, userId)
                // Success is handled by the Flow observer in the ViewModel
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MainViewModel", "Failed to toggle task", e)
                }
            } finally {
                activeToggleJobs.remove(docId)
            }
        }
    }

    fun updateTaskDescription(task: Task, newDescription: String) {
        val trimmedDesc = newDescription.trim()
        if (trimmedDesc.isEmpty()) {
            _saveStatus.value = Resource.Error("Task description cannot be empty.")
            return
        }
        if (trimmedDesc.length > 500) {
            _saveStatus.value = Resource.Error("Task description is too long.")
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            _saveStatus.value = Resource.Error("Must be logged in.")
            return
        }

        _saveStatus.value = Resource.Loading
        val updatedTask = task.copy(description = trimmedDesc)

        viewModelScope.launch {
            try {
                repository.updateTask(updatedTask, userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to update task description: ${e.message}")
            }
        }
    }

    fun deleteTask(task: Task) {
        val docId = task.docId
        if (docId.isEmpty()) return

        val userId = auth.currentUser?.uid ?: return

        _saveStatus.value = Resource.Loading
        viewModelScope.launch {
            try {
                repository.deleteTask(docId, userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to delete task: ${e.message}")
            }
        }
    }

    fun clearCompletedTasks() {
        val userId = auth.currentUser?.uid ?: return
        _saveStatus.value = Resource.Loading
        
        viewModelScope.launch {
            try {
                repository.clearCompletedTasks(userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to clear completed: ${e.message}")
            }
        }
    }

    fun deleteAllTasks() {
        val userId = auth.currentUser?.uid ?: return
        _saveStatus.value = Resource.Loading
        
        viewModelScope.launch {
            try {
                repository.deleteAllTasks(userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to delete all tasks: ${e.message}")
            }
        }
    }


    fun resetSaveStatus() {
        _saveStatus.value = null
    }

    fun toggleDailyReminder(enabled: Boolean, context: Context) {
        sharedPreferences.edit().putBoolean("daily_reminders_enabled", enabled).apply()
        _isDailyReminderEnabled.value = enabled
        
        if (enabled) {
            com.example.irondiary.util.NotificationHelper.scheduleDailyReminder(context)
        } else {
            com.example.irondiary.util.NotificationHelper.cancelDailyReminder(context)
        }
    }

    override fun onCleared() {
        super.onCleared()
        dailyLogsJob?.cancel()
        weightDataJob?.cancel()
        studySessionsJob?.cancel()
        tasksJob?.cancel()
        activeToggleJobs.values.forEach { it.cancel() }
        activeSaveJobs.values.forEach { it.cancel() }
    }
}
