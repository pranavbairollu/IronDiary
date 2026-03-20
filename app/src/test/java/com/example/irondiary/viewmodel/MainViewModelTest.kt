package com.example.irondiary.viewmodel

import android.app.Application
import app.cash.turbine.test
import com.example.irondiary.data.Resource
import com.example.irondiary.util.MainDispatcherRule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.example.irondiary.data.repository.IronDiaryRepository
import com.google.firebase.Timestamp
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
    fun toggleTaskCompletion_debouncesMultipleClicks_callsRepositoryOnce() = runTest(mainDispatcherRule.testDispatcher) {
        val task = com.example.irondiary.data.model.Task(docId = "task_1", description = "Test Task")
        
        // Rapid clicks
        viewModel.toggleTaskCompletion(task)
        viewModel.toggleTaskCompletion(task)
        viewModel.toggleTaskCompletion(task)

        // Ensure the launch happened
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()

        // Wait for debounce period (300ms)
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(400)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        
        coVerify(exactly = 1) { repositoryMock.updateTask(any(), "test_uid") }
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

    @Test
    fun repository_addTask_throwsOnBlankDescription() = runTest {
        val repo = IronDiaryRepository(applicationMock)
        try {
            repo.addTask("uid", "  ")
            assertTrue("Should have thrown IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertEquals("Task description cannot be empty", e.message)
        }
    }
}
