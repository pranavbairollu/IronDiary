package com.example.irondiary.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: FirebaseUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    object PasswordResetEmailSent : AuthUiState()
}

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        auth.currentUser?.let {
            _uiState.value = AuthUiState.Success(it)
        }
    }

    fun sendPasswordResetEmail(email: String) {
        if (!isValidEmail(email)) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                auth.sendPasswordResetEmail(email).await()
                _uiState.value = AuthUiState.PasswordResetEmailSent
            } catch (e: FirebaseAuthException) {
                _uiState.value = AuthUiState.Error(mapFirebaseError(e))
            } catch (e: FirebaseNetworkException) {
                _uiState.value = AuthUiState.Error("No internet connection.")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (!isValidEmail(email)) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            _uiState.value = AuthUiState.Error("Password cannot be empty.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.let {
                    _uiState.value = AuthUiState.Success(it)
                }
            } catch (e: FirebaseAuthException) {
                _uiState.value = AuthUiState.Error(mapFirebaseError(e))
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _uiState.value = AuthUiState.Error("Invalid credentials.")
            } catch (e: FirebaseNetworkException) {
                _uiState.value = AuthUiState.Error("No internet connection.")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (!isValidEmail(email)) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (!isValidPassword(password)) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                result.user?.let {
                    _uiState.value = AuthUiState.Success(it)
                }
            } catch (e: FirebaseAuthException) {
                _uiState.value = AuthUiState.Error(mapFirebaseError(e))
            } catch (e: FirebaseNetworkException) {
                _uiState.value = AuthUiState.Error("No internet connection.")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _uiState.value = AuthUiState.Idle
    }

    fun resetAuthState() {
        _uiState.value = AuthUiState.Idle
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"
        return email.isNotBlank() && email.matches(emailPattern.toRegex())
    }

    private fun isValidPassword(password: String): Boolean {
        // Firebase Auth enforces a minimum of 6 characters
        return password.isNotBlank() && password.length >= 6
    }

    private fun mapFirebaseError(e: FirebaseAuthException): String {
        return when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Invalid email address."
            "ERROR_WRONG_PASSWORD" -> "Incorrect password."
            "ERROR_USER_NOT_FOUND" -> "No account found with this email."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "This email is already in use."
            "ERROR_WEAK_PASSWORD" -> "The password is too weak. (Min 6 chars)"
            "ERROR_INVALID_CREDENTIAL" -> "Authentication failed: Check your email and password."
            "ERROR_USER_DISABLED" -> "This account has been disabled."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
            else -> "Authentication failed: ${e.message ?: "Please try again."}"
        }
    }
}
