package com.example.irondiary.viewmodel

import android.app.Application
import android.content.SharedPreferences
import app.cash.turbine.test
import com.example.irondiary.data.Resource
import com.example.irondiary.util.MainDispatcherRule
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.android.gms.tasks.OnCompleteListener
import com.example.irondiary.data.repository.IronDiaryRepository
import com.google.firebase.Timestamp
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authMock: FirebaseAuth
    private lateinit var applicationMock: Application
    private lateinit var repositoryMock: IronDiaryRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        mockkStatic(FirebaseAuth::class)

        authMock = mockk(relaxed = true)
        applicationMock = mockk(relaxed = true)
        repositoryMock = mockk(relaxed = true)

        every { FirebaseAuth.getInstance() } returns authMock
        
        coEvery { repositoryMock.getTasks(any()) } returns flowOf(emptyList())
        coEvery { repositoryMock.getStudySessions(any()) } returns flowOf(emptyList())
        coEvery { repositoryMock.getDailyLogs(any()) } returns flowOf(emptyMap())
        coEvery { repositoryMock.getWeightData(any()) } returns flowOf(emptyList())
        
        every { repositoryMock.context } returns applicationMock
        every { applicationMock.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)

        val mockUser = mockk<FirebaseUser>(relaxed = true)
        every { mockUser.uid } returns "test_uid"
        every { authMock.currentUser } returns mockUser

        viewModel = MainViewModel(repositoryMock)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun addTask_blankInput_emitsErrorWithoutFirebaseCall() = runTest {
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.addTask("   ")
            val errorState = awaitItem()
            assertTrue(errorState is Resource.Error)
            assertEquals("Task description cannot be empty.", (errorState as Resource.Error).message)
            coVerify(exactly = 0) { repositoryMock.addTask(any(), any()) }
        }
    }

    @Test
    fun addTask_exceedsLength_emitsErrorWithoutFirebaseCall() = runTest {
        val longTask = "A".repeat(501)
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.addTask(longTask)
            val errorState = awaitItem()
            assertTrue(errorState is Resource.Error)
            assertEquals("Task description is too long.", (errorState as Resource.Error).message)
            coVerify(exactly = 0) { repositoryMock.addTask(any(), any()) }
        }
    }

    @Test
    fun addTask_validInput_emitsSuccess() = runTest {
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.addTask("Read physics textbook")
            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Success(Unit), awaitItem())
            coVerify(exactly = 1) { repositoryMock.addTask("test_uid", "Read physics textbook") }
        }
    }

    @Test
    fun addStudySession_emptySubject_emitsErrorWithoutFirebaseCall() = runTest {
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.addStudySession("", 2f)
            val errorState = awaitItem()
            assertTrue(errorState is Resource.Error)
            assertEquals("Subject cannot be empty.", (errorState as Resource.Error).message)
            coVerify(exactly = 0) { repositoryMock.addStudySession(any(), any()) }
        }
    }

    @Test
    fun addStudySession_invalidDuration_emitsErrorWithoutFirebaseCall() = runTest {
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.addStudySession("Math", 0f)
            assertEquals("Duration must be between 0 and 24 hours.", (awaitItem() as Resource.Error).message)
            viewModel.resetSaveStatus()
            assertNull(awaitItem())
            viewModel.addStudySession("Math", 25f)
            assertEquals("Duration must be between 0 and 24 hours.", (awaitItem() as Resource.Error).message)
            coVerify(exactly = 0) { repositoryMock.addStudySession(any(), any()) }
        }
    }

    @Test
    fun addStudySession_validInput_emitsSuccess() = runTest {
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.addStudySession("Computer Science", 2.5f)
            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Success(Unit), awaitItem())
            coVerify(exactly = 1) { repositoryMock.addStudySession(any(), "test_uid") }
        }
    }

    @Test
    fun saveDailyLog_validInput_emitsSuccess() = runTest {
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            val log = com.example.irondiary.data.DailyLog(date = "2024-03-19", attendedGym = true)
            viewModel.saveDailyLog("2024-03-19", log)
            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Success(Unit), awaitItem())
            coVerify(exactly = 1) { repositoryMock.saveDailyLog(any(), "test_uid") }
        }
    }

    @Test
    fun saveFailed_networkError_emitsError() = runTest {
        val exception = Exception("Network offline")
        coEvery { repositoryMock.addTask(any(), any()) } throws exception
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.addTask("Offline Task")
            assertEquals(Resource.Loading, awaitItem())
            val errorState = awaitItem()
            assertTrue(errorState is Resource.Error)
            assertTrue((errorState as Resource.Error).message.contains("Network offline"))
        }
    }

    @Test
    fun userNull_addTask_abortsGracefully() = runTest {
        every { authMock.currentUser } returns null
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.addTask("Task without user")
            val errorState = awaitItem()
            assertTrue(errorState is Resource.Error)
            assertEquals("Must be logged in.", (errorState as Resource.Error).message)
            coVerify(exactly = 0) { repositoryMock.addTask(any(), any()) }
        }
    }

    @Test
    fun updateTaskDescription_validInput_emitsSuccess() = runTest {
        val task = com.example.irondiary.data.model.Task(docId = "task_1", description = "Old Desc")
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.updateTaskDescription(task, "New Desc")
            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Success(Unit), awaitItem())
            coVerify(exactly = 1) { repositoryMock.updateTask(any(), "test_uid") }
        }
    }
}
