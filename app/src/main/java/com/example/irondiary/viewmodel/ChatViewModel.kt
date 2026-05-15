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
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val graphData: List<Float>? = null
)

class ChatViewModel(
    private val repository: IronDiaryRepository
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("Hi! I'm your Iron Assistant. Ask me anything about your weight or gym progress!", false)
    ))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _topInsight = MutableStateFlow<String?>(null)
    val topInsight: StateFlow<String?> = _topInsight.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    private var currentDataBundle: LocalDataBundle? = null

    private var isWelcomeSent = false

    init {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModelScope.launch {
                combine(
                    repository.getWeightData(userId),
                    repository.getTasks(userId),
                    repository.getStudySessions(userId)
                ) { weights, tasks, sessions ->
                    LocalDataBundle(weights, tasks, sessions)
                }.collect { bundle ->
                    currentDataBundle = bundle
                    
                    // Update Top Insight
                    _topInsight.value = IronIntelligenceEngine.getTopInsight(bundle)
                    
                    // Proactive Nudge
                    if (!isWelcomeSent && _messages.value.size <= 1) {
                        val welcome = IronIntelligenceEngine.getWelcomeMessage(bundle)
                        _messages.value = listOf(ChatMessage(welcome, false))
                        isWelcomeSent = true
                    }
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(text, true)
        _messages.value = _messages.value + userMessage
        _isTyping.value = true

        viewModelScope.launch {
            // Add a small artificial delay for "premium" feel and to show typing indicator
            kotlinx.coroutines.delay(1200)
            
            val response = currentDataBundle?.let {
                IronIntelligenceEngine.processQuery(text, it)
            } ?: com.example.irondiary.util.IntelligenceResponse("I'm still loading your data. Please try again in a moment!")

            _messages.value = _messages.value + ChatMessage(
                text = response.text,
                isUser = false,
                graphData = response.graphData
            )
            _isTyping.value = false
        }
    }

    fun toggleVoiceInput(context: android.content.Context) {
        if (_isListening.value) {
            speechRecognizer?.stopListening()
            _isListening.value = false
        } else {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { _isListening.value = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _isListening.value = false }
                override fun onError(error: Int) { 
                    _isListening.value = false 
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        sendMessage(matches[0])
                    }
                    _isListening.value = false
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            speechRecognizer?.startListening(intent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
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
