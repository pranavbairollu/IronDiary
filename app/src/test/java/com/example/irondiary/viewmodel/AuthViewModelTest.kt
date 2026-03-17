package com.example.irondiary.viewmodel

import app.cash.turbine.test
import com.example.irondiary.util.MainDispatcherRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authMock: FirebaseAuth
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        mockkStatic(FirebaseAuth::class)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        authMock = mockk(relaxed = true)
        every { FirebaseAuth.getInstance() } returns authMock
        // Ensure initial currentUser is null by default so it starts in Idle state
        every { authMock.currentUser } returns null

        viewModel = AuthViewModel()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun init_withExistingUser_emitsSuccess() = runTest {
        val mockUser = mockk<FirebaseUser>(relaxed = true)
        every { authMock.currentUser } returns mockUser
        
        // Re-init view model to capture init block behavior
        val newViewModel = AuthViewModel()
        
        newViewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is AuthUiState.Success)
            assertEquals(mockUser, (state as AuthUiState.Success).user)
        }
    }

    @Test
    fun signIn_blankInput_emitsErrorWithoutFirebaseCall() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            
            viewModel.signIn("   ", "password")
            
            val errorState = awaitItem()
            assertTrue(errorState is AuthUiState.Error)
            assertEquals("Email and password cannot be empty.", (errorState as AuthUiState.Error).message)
            
            // Verify no interaction with Firebase
            verify(exactly = 0) { authMock.signInWithEmailAndPassword(any(), any()) }
        }
    }

    @Test
    fun signIn_invalidCredentials_emitsError() = runTest {
        val testEmail = "test@example.com"
        val testPassword = "wrongpassword"
        val exception = mockk<FirebaseAuthInvalidCredentialsException>(relaxed = true)
        every { exception.errorCode } returns "ERROR_WRONG_PASSWORD"
        every { exception.message } returns "message"
        
        val mockTask = mockk<Task<AuthResult>>()
        every { authMock.signInWithEmailAndPassword(testEmail, testPassword) } returns mockTask
        coEvery { mockTask.await() } throws exception

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            
            viewModel.signIn(testEmail, testPassword)
            
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem()
            assertTrue(errorState is AuthUiState.Error)
            assertEquals("Incorrect password.", (errorState as AuthUiState.Error).message)
        }
    }

    @Test
    fun signIn_networkError_emitsError() = runTest {
        val testEmail = "test@example.com"
        val testPassword = "password"
        val exception = mockk<FirebaseNetworkException>(relaxed = true)
        every { exception.message } returns "Network error"
        
        val mockTask = mockk<Task<AuthResult>>()
        every { authMock.signInWithEmailAndPassword(testEmail, testPassword) } returns mockTask
        coEvery { mockTask.await() } throws exception

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            
            viewModel.signIn(testEmail, testPassword)
            
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem()
            assertTrue(errorState is AuthUiState.Error)
            assertEquals("No internet connection.", (errorState as AuthUiState.Error).message)
        }
    }

    @Test
    fun signIn_success_emitsSuccess() = runTest {
        val testEmail = "test@example.com"
        val testPassword = "password"
        val mockUser = mockk<FirebaseUser>(relaxed = true)
        val mockAuthResult = mockk<AuthResult>()
        every { mockAuthResult.user } returns mockUser
        
        val mockTask = mockk<Task<AuthResult>>()
        every { authMock.signInWithEmailAndPassword(testEmail, testPassword) } returns mockTask
        coEvery { mockTask.await() } returns mockAuthResult

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            
            viewModel.signIn(testEmail, testPassword)
            
            assertEquals(AuthUiState.Loading, awaitItem())
            val successState = awaitItem()
            assertTrue(successState is AuthUiState.Success)
            assertEquals(mockUser, (successState as AuthUiState.Success).user)
        }
    }
    
    @Test
    fun signUp_blankInput_emitsErrorWithoutFirebaseCall() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            
            viewModel.signUp("email@test.com", "  ")
            
            val errorState = awaitItem()
            assertTrue(errorState is AuthUiState.Error)
            assertEquals("Email and password cannot be empty.", (errorState as AuthUiState.Error).message)
            verify(exactly = 0) { authMock.createUserWithEmailAndPassword(any(), any()) }
        }
    }

    @Test
    fun signUp_success_emitsSuccess() = runTest {
        val testEmail = "test@example.com"
        val testPassword = "password"
        val mockUser = mockk<FirebaseUser>(relaxed = true)
        val mockAuthResult = mockk<AuthResult>()
        every { mockAuthResult.user } returns mockUser
        
        val mockTask = mockk<Task<AuthResult>>()
        every { authMock.createUserWithEmailAndPassword(testEmail, testPassword) } returns mockTask
        coEvery { mockTask.await() } returns mockAuthResult

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            
            viewModel.signUp(testEmail, testPassword)
            
            assertEquals(AuthUiState.Loading, awaitItem())
            val successState = awaitItem()
            assertTrue(successState is AuthUiState.Success)
            assertEquals(mockUser, (successState as AuthUiState.Success).user)
        }
    }

    @Test
    fun sendPasswordResetEmail_blankInput_emitsError() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            
            viewModel.sendPasswordResetEmail("   ")
            
            val errorState = awaitItem()
            assertTrue(errorState is AuthUiState.Error)
            assertEquals("Email cannot be empty.", (errorState as AuthUiState.Error).message)
            verify(exactly = 0) { authMock.sendPasswordResetEmail(any()) }
        }
    }

    @Test
    fun sendPasswordResetEmail_success_emitsSuccess() = runTest {
        val testEmail = "test@example.com"
        val mockTask = mockk<Task<Void>>()
        every { authMock.sendPasswordResetEmail(testEmail) } returns mockTask
        coEvery { mockTask.await() } returns mockk()

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            
            viewModel.sendPasswordResetEmail(testEmail)
            
            assertEquals(AuthUiState.Loading, awaitItem())
            val successState = awaitItem()
            assertEquals(AuthUiState.PasswordResetEmailSent, successState)
        }
    }
}
