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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val sharedPreferences = application.getSharedPreferences("IronDiaryPrefs", Context.MODE_PRIVATE)

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
    private var tasksListener: ListenerRegistration? = null

    init {
        fetchDailyLogs()
        fetchWeightData()
        fetchStudySessions()
        fetchTasks()
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
                        val logs = it.documents.associate { doc ->
                            doc.id to (doc.toObject<DailyLog>() ?: DailyLog(date = doc.id))
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
                            val weights = it.toObjects(DailyLog::class.java)
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
                        val sessions = it.toObjects(StudySession::class.java)
                        _studySessions.value = Resource.Success(sessions)
                    }
                } catch (e: Exception) {
                    _studySessions.value = Resource.Error("Error parsing study sessions: ${e.message}")
                }
            }
        }
    }

    fun fetchTasks() {
        viewModelScope.launch {
            _tasks.value = Resource.Loading
            val collection = getTasksCollection()
            if (collection == null) {
                _tasks.value = Resource.Error("You must be logged in to see your data.")
                return@launch
            }

            tasksListener?.remove()
            tasksListener = collection.orderBy("createdDate").addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _tasks.value = Resource.Error("Failed to fetch tasks: ${e.message}")
                    return@addSnapshotListener
                }
                try {
                    snapshot?.let {
                        val tasks = it.toObjects(Task::class.java)
                        _tasks.value = Resource.Success(tasks)
                    }
                } catch (e: Exception) {
                    _tasks.value = Resource.Error("Error parsing tasks: ${e.message}")
                }
            }
        }
    }

    fun saveDailyLog(dateId: String, log: DailyLog) {
        viewModelScope.launch {
            _saveStatus.value = Resource.Loading
            try {
                getLogsCollection()?.document(dateId)?.set(log)?.await()
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to save log: ${e.message}")
            }
        }
    }

    fun addStudySession(subject: String, duration: Float) {
        viewModelScope.launch {
            _saveStatus.value = Resource.Loading
            try {
                val session = StudySession(subject = subject, date = com.google.firebase.Timestamp(Date()), duration = duration.toDouble())
                getStudySessionsCollection()?.add(session)?.await()
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to save study session: ${e.message}")
            }
        }
    }

    fun addTask(description: String) {
        viewModelScope.launch {
            _saveStatus.value = Resource.Loading
            try {
                val task = Task(description = description, createdDate = com.google.firebase.Timestamp(Date()))
                getTasksCollection()?.add(task)?.await()
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to save task: ${e.message}")
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            _saveStatus.value = Resource.Loading
            try {
                val newCompletedStatus = !task.completed
                val update = if (newCompletedStatus) {
                    mapOf(
                        "completed" to true,
                        "completedDate" to com.google.firebase.Timestamp(Date())
                    )
                } else {
                    mapOf(
                        "completed" to false,
                        "completedDate" to null
                    )
                }
                getTasksCollection()?.document(task.docId)?.update(update)?.await()
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to update task: ${e.message}")
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            _saveStatus.value = Resource.Loading
            try {
                getTasksCollection()?.document(task.docId)?.delete()?.await()
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to delete task: ${e.message}")
            }
        }
    }

    fun checkAndResetTasks() {
        val today = LocalDate.now().toString()
        val lastResetDate = sharedPreferences.getString("lastResetDate", null)

        if (today != lastResetDate) {
            resetPendingTasks()
            sharedPreferences.edit().putString("lastResetDate", today).apply()
        }
    }

    fun resetPendingTasks() {
        viewModelScope.launch {
            try {
                val tasksCollection = getTasksCollection() ?: return@launch
                val pendingTasks = tasksCollection.whereEqualTo("completed", false).get().await()
                for (document in pendingTasks.documents) {
                    document.reference.delete().await()
                }
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to reset pending tasks: ${e.message}")
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
        tasksListener?.remove()
    }
}
