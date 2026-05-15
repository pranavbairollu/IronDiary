package com.example.irondiary.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.model.Task
import com.example.irondiary.data.repository.IronDiaryRepository
import com.example.irondiary.util.IronIntelligenceEngine
import com.example.irondiary.util.LocalDataBundle
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatViewModel(
    private val repository: IronDiaryRepository
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("Hi! I'm your Iron Assistant. Ask me anything about your weight or gym progress!", false)
    ))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var currentDataBundle: LocalDataBundle? = null

    init {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModelScope.launch {
                combine(
                    repository.getWeightData(userId),
                    repository.getTasks(userId)
                ) { weights, tasks ->
                    LocalDataBundle(weights, tasks)
                }.collect { bundle ->
                    currentDataBundle = bundle
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(text, true)
        _messages.value = _messages.value + userMessage

        viewModelScope.launch {
            // Add a small artificial delay for "premium" feel
            kotlinx.coroutines.delay(500)
            
            val response = currentDataBundle?.let {
                IronIntelligenceEngine.processQuery(text, it)
            } ?: "I'm still loading your data. Please try again in a moment!"

            _messages.value = _messages.value + ChatMessage(response, false)
        }
    }
}

class ChatViewModelFactory(private val repository: IronDiaryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
