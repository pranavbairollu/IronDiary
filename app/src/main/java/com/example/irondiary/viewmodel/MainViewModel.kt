package com.example.irondiary.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.Resource
import com.example.irondiary.data.model.StudySession
import com.example.irondiary.data.model.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.Date

import kotlinx.coroutines.Job
import com.example.irondiary.data.repository.IronDiaryRepository

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val sharedPreferences = application.getSharedPreferences("IronDiaryPrefs", Context.MODE_PRIVATE)
    
    private val repository = IronDiaryRepository(application)

    private val _dailyLogs = MutableStateFlow<Resource<Map<String, DailyLog>>>(Resource.Success(emptyMap()))
    val dailyLogs: StateFlow<Resource<Map<String, DailyLog>>> = _dailyLogs.asStateFlow()

    private val _weightData = MutableStateFlow<Resource<List<DailyLog>>>(Resource.Success(emptyList()))
    val weightData: StateFlow<Resource<List<DailyLog>>> = _weightData.asStateFlow()

    private val _studySessions = MutableStateFlow<Resource<List<StudySession>>>(Resource.Success(emptyList()))
    val studySessions: StateFlow<Resource<List<StudySession>>> = _studySessions.asStateFlow()

    private val _tasks = MutableStateFlow<Resource<List<Task>>>(Resource.Success(emptyList()))
    val tasks: StateFlow<Resource<List<Task>>> = _tasks.asStateFlow()

    private val _saveStatus = MutableStateFlow<Resource<Unit>?>(null)
    val saveStatus: StateFlow<Resource<Unit>?> = _saveStatus.asStateFlow()

    private var dailyLogsListener: ListenerRegistration? = null
    private var weightDataListener: ListenerRegistration? = null
    private var studySessionsListener: ListenerRegistration? = null
    private var tasksJob: Job? = null

    init {
        fetchDailyLogs()
        fetchWeightData()
        fetchStudySessions()
        fetchTasks()
        
        // Trigger background sync on app startup (Safeguard #4)
        repository.enqueueSync()
    }

    private fun getLogsCollection() = auth.currentUser?.uid?.let {
        firestore.collection("users").document(it).collection("daily_logs")
    }

    private fun getStudySessionsCollection() = auth.currentUser?.uid?.let {
        firestore.collection("users").document(it).collection("study_sessions")
    }

    private fun getTasksCollection() = auth.currentUser?.uid?.let {
        firestore.collection("users").document(it).collection("tasks")
    }

    fun fetchDailyLogs() {
        viewModelScope.launch {
            _dailyLogs.value = Resource.Loading
            val collection = getLogsCollection()
            if (collection == null) {
                _dailyLogs.value = Resource.Error("You must be logged in to see your data.")
                return@launch
            }

            dailyLogsListener?.remove()
            dailyLogsListener = collection.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _dailyLogs.value = Resource.Error("Failed to fetch logs: ${e.message}")
                    return@addSnapshotListener
                }
                try {
                    snapshot?.let {
                        val logs = mutableMapOf<String, DailyLog>()
                        for (doc in it.documents) {
                            try {
                                val log = doc.toObject<DailyLog>() ?: DailyLog(date = doc.id)
                                logs[doc.id] = log
                            } catch (parseException: Exception) {
                                android.util.Log.w("MainViewModel", "Failed to parse DailyLog: ${doc.id}", parseException)
                            }
                        }
                        _dailyLogs.value = Resource.Success(logs)
                    }
                } catch (e: Exception) {
                    _dailyLogs.value = Resource.Error("Error parsing logs: ${e.message}")
                }
            }
        }
    }

    fun fetchWeightData() {
        viewModelScope.launch {
            _weightData.value = Resource.Loading
            val collection = getLogsCollection()
            if (collection == null) {
                _weightData.value = Resource.Error("You must be logged in to see your data.")
                return@launch
            }

            weightDataListener?.remove()
            weightDataListener = collection.whereGreaterThan("weight", 0).orderBy("date")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        _weightData.value = Resource.Error("Failed to fetch weight data: ${e.message}")
                        return@addSnapshotListener
                    }
                    try {
                        snapshot?.let {
                            val weights = mutableListOf<DailyLog>()
                            for (doc in it.documents) {
                                try {
                                    val weightLog = doc.toObject(DailyLog::class.java)
                                    if (weightLog != null) weights.add(weightLog)
                                } catch (parseException: Exception) {
                                    android.util.Log.w("MainViewModel", "Failed to parse DailyLog for weight: ${doc.id}", parseException)
                                }
                            }
                            _weightData.value = Resource.Success(weights)
                        }
                    } catch (e: Exception) {
                        _weightData.value = Resource.Error("Error parsing weight data: ${e.message}")
                    }
                }
        }
    }

    fun fetchStudySessions() {
        viewModelScope.launch {
            _studySessions.value = Resource.Loading
            val collection = getStudySessionsCollection()
            if (collection == null) {
                _studySessions.value = Resource.Error("You must be logged in to see your data.")
                return@launch
            }

            studySessionsListener?.remove()
            studySessionsListener = collection.orderBy("date").addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _studySessions.value = Resource.Error("Failed to fetch study sessions: ${e.message}")
                    return@addSnapshotListener
                }
                try {
                    snapshot?.let {
                        val sessions = mutableListOf<StudySession>()
                        for (doc in it.documents) {
                            try {
                                val session = doc.toObject(StudySession::class.java)
                                if (session != null) sessions.add(session)
                            } catch (parseException: Exception) {
                                android.util.Log.w("MainViewModel", "Failed to parse StudySession: ${doc.id}", parseException)
                            }
                        }
                        _studySessions.value = Resource.Success(sessions)
                    }
                } catch (e: Exception) {
                    _studySessions.value = Resource.Error("Error parsing study sessions: ${e.message}")
                }
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

    fun saveDailyLog(dateId: String, log: DailyLog) {
        _saveStatus.value = Resource.Loading
        getLogsCollection()?.document(dateId)?.set(log)
            ?.addOnSuccessListener {
                _saveStatus.value = Resource.Success(Unit)
            }
            ?.addOnFailureListener { e ->
                _saveStatus.value = Resource.Error("Failed to save log: ${e.message}")
            }
    }

    fun addStudySession(subject: String, duration: Float) {
        val trimmedSubject = subject.trim()
        if (trimmedSubject.isEmpty()) {
            _saveStatus.value = Resource.Error("Subject cannot be empty.")
            return
        }
        if (duration <= 0f || duration > 24f) {
            _saveStatus.value = Resource.Error("Duration must be between 0 and 24 hours.")
            return
        }

        _saveStatus.value = Resource.Loading
        val session = StudySession(subject = trimmedSubject, date = com.google.firebase.Timestamp(Date()), duration = duration.toDouble())
        getStudySessionsCollection()?.add(session)
            ?.addOnSuccessListener {
                _saveStatus.value = Resource.Success(Unit)
            }
            ?.addOnFailureListener { e ->
                _saveStatus.value = Resource.Error("Failed to save study session: ${e.message}")
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
        val task = Task(description = trimmedDesc, createdDate = com.google.firebase.Timestamp(Date()))
        viewModelScope.launch {
            try {
                repository.addTask(task, userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to save task: ${e.message}")
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        val docId = task.docId
        if (docId.isEmpty()) return

        val userId = auth.currentUser?.uid
        if (userId == null) {
            _saveStatus.value = Resource.Error("Must be logged in.")
            return
        }

        _saveStatus.value = Resource.Loading
        val newCompletedStatus = !task.completed
        val updatedTask = task.copy(
            completed = newCompletedStatus,
            completedDate = if (newCompletedStatus) com.google.firebase.Timestamp.now() else null
        )

        viewModelScope.launch {
            try {
                repository.updateTask(updatedTask, userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to update task: ${e.message}")
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

    override fun onCleared() {
        super.onCleared()
        dailyLogsListener?.remove()
        weightDataListener?.remove()
        studySessionsListener?.remove()
        tasksJob?.cancel()
    }
}
