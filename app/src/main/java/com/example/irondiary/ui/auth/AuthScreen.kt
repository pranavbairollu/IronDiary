package com.example.irondiary.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.irondiary.viewmodel.AuthUiState
import com.example.irondiary.viewmodel.AuthViewModel

@Composable
fun AuthScreen(onSignIn: () -> Unit) {
    val authViewModel: AuthViewModel = viewModel()
    val uiState by authViewModel.uiState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onSignIn()
        }
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            onDismiss = { showForgotPasswordDialog = false },
            onSend = { emailAddress -> authViewModel.sendPasswordResetEmail(emailAddress) },
            viewModel = authViewModel
        )
    }

    if (uiState is AuthUiState.PasswordResetEmailSent) {
        AlertDialog(
            onDismissRequest = { authViewModel.resetAuthState() },
            title = { Text("Password Reset") },
            text = { Text("A password reset email has been sent to your email address.") },
            confirmButton = {
                Button(onClick = { authViewModel.resetAuthState() }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Iron Diary", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                if (uiState is AuthUiState.Error) authViewModel.resetAuthState()
            },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = uiState is AuthUiState.Error && (uiState as AuthUiState.Error).message.contains("email", ignoreCase = true)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { 
                password = it
                if (uiState is AuthUiState.Error) authViewModel.resetAuthState()
            },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = uiState is AuthUiState.Error && (uiState as AuthUiState.Error).message.contains("password", ignoreCase = true)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (uiState is AuthUiState.Error) {
            Text(
                text = (uiState as AuthUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Start)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (uiState) {
            is AuthUiState.Loading -> {
                CircularProgressIndicator()
            }
            else -> {
                AuthButtons(email, password, authViewModel) { showForgotPasswordDialog = true }
            }
        }
    }
}

@Composable
private fun AuthButtons(
    email: String,
    password: String,
    authViewModel: AuthViewModel,
    onForgotPasswordClicked: () -> Unit
) {
    val isFormValid = email.isNotBlank() && password.isNotBlank()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { authViewModel.signIn(email, password) },
                modifier = Modifier.weight(1f),
                enabled = isFormValid
            ) {
                Text("Sign In")
            }
            OutlinedButton(
                onClick = { authViewModel.signUp(email, password) },
                modifier = Modifier.weight(1f),
                enabled = isFormValid
            ) {
                Text("Sign Up")
            }
        }
        TextButton(
            onClick = onForgotPasswordClicked,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Forgot Password?")
        }
    }
}

@Composable
fun ForgotPasswordDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    viewModel: AuthViewModel
) {
    var email by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Password") },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Enter your email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState is AuthUiState.Error
                )
                if (uiState is AuthUiState.Error) {
                    val errorMessage = (uiState as AuthUiState.Error).message
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(email) },
                enabled = email.isNotBlank() && uiState !is AuthUiState.Loading
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator()
                } else {
                    Text("Send")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
