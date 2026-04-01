package com.example.irondiary.viewmodel

import android.app.Application
import android.util.Log
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
        mockkStatic(Log::class)

        authMock = mockk(relaxed = true)
        
        // Mock Log methods to prevent "Method not mocked" errors
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
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
    fun saveDailyLog_invalidWeight_emitsError() = runTest {
        val invalidLog = com.example.irondiary.data.DailyLog(date = "2026-03-24", weight = -10f)
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.saveDailyLog(invalidLog)
            val errorState = awaitItem()
            assertTrue(errorState is Resource.Error)
            assertEquals("Weight must be between 0 and 500 kg.", (errorState as Resource.Error).message)
            coVerify(exactly = 0) { repositoryMock.saveDailyLog(any(), any()) }
        }
    }

    @Test
    fun saveDailyLog_longNotes_emitsError() = runTest {
        val longNotes = "a".repeat(2001)
        val invalidLog = com.example.irondiary.data.DailyLog(date = "2026-03-24", notes = longNotes)
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.saveDailyLog(invalidLog)
            val errorState = awaitItem()
            assertTrue(errorState is Resource.Error)
            assertEquals("Notes cannot exceed 2000 characters.", (errorState as Resource.Error).message)
            coVerify(exactly = 0) { repositoryMock.saveDailyLog(any(), any()) }
        }
    }

    @Test
    fun saveDailyLog_validInput_emitsSuccess() = runTest {
        val validLog = com.example.irondiary.data.DailyLog(date = "2026-03-24", weight = 75f, notes = "Feeling good")
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            viewModel.saveDailyLog(validLog)
            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Success(Unit), awaitItem())
            coVerify(exactly = 1) { repositoryMock.saveDailyLog(any(), "test_uid") }
        }
    }

    @Test
    fun saveDailyLog_boundaryValues_emitsSuccess() = runTest {
        viewModel.saveStatus.test {
            assertNull(awaitItem())

            // Test lower boundary: 0.1 kg
            val lowerBoundaryLog = com.example.irondiary.data.DailyLog(date = "2026-03-24", weight = 0.1f)
            viewModel.saveDailyLog(lowerBoundaryLog)
            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Success(Unit), awaitItem())

            // Test upper boundary: 500.0 kg
            val upperBoundaryLog = com.example.irondiary.data.DailyLog(date = "2026-03-25", weight = 500.0f)
            viewModel.saveDailyLog(upperBoundaryLog)
            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Success(Unit), awaitItem())

            // Test exactly 2000 characters
            val exactNotes = "a".repeat(2000)
            val boundaryNotesLog = com.example.irondiary.data.DailyLog(date = "2026-03-26", notes = exactNotes)
            viewModel.saveDailyLog(boundaryNotesLog)
            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Success(Unit), awaitItem())
        }
    }

    @Test
    fun saveDailyLog_concurrentCalls_cancelsPreviousJob() = runTest {
        val log1 = com.example.irondiary.data.DailyLog(date = "2026-03-24", weight = 70f)
        val log2 = com.example.irondiary.data.DailyLog(date = "2026-03-24", weight = 75f)

        // Start first save
        viewModel.saveDailyLog(log1)
        
        // Start second save immediately for the same date
        viewModel.saveDailyLog(log2)

        // Advance time to allow completion of debounce
        testScheduler.advanceTimeBy(500)
        testScheduler.runCurrent()

        // Verify repository was called for the SECOND log
        coVerify(exactly = 1) { repositoryMock.saveDailyLog(log2, "test_uid") }
        // The first call might have been cancelled before reaching the repository or overwritten
    }

    @Test
    fun repository_saveDailyLog_throwsOnInvalidWeight() = runTest {
        val repo = IronDiaryRepository(applicationMock)
        val invalidLog = com.example.irondiary.data.DailyLog(date = "2026-03-24", weight = 600f)
        try {
            repo.saveDailyLog(invalidLog, "uid")
            assertTrue("Should have thrown IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertEquals("Weight must be between 0 and 500 kg", e.message)
        }
    }

    @Test
    fun calculateStats_streakWithGap_resetsToZero() = runTest {
        val today = java.time.LocalDate.now()
        
        // Logs with a gap (Today and Yesterday missing)
        val logs = mapOf(
            today.minusDays(2).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) to com.example.irondiary.data.DailyLog(attendedGym = true),
            today.minusDays(3).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) to com.example.irondiary.data.DailyLog(attendedGym = true)
        )
        
        coEvery { repositoryMock.getDailyLogs(any()) } returns flowOf(logs)
        viewModel = MainViewModel(repositoryMock)
        
        testScheduler.runCurrent()
        assertEquals(0, viewModel.gymStreak.value)
    }

    @Test
    fun calculateStats_continuousStreak_calculatesCorrectly() = runTest {
        val today = java.time.LocalDate.now()
        
        // Mock 3 consecutive days of gym attendance tracking back from today
        val logs = mapOf(
            today.minusDays(0).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) to com.example.irondiary.data.DailyLog(attendedGym = true),
            today.minusDays(1).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) to com.example.irondiary.data.DailyLog(attendedGym = true),
            today.minusDays(2).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) to com.example.irondiary.data.DailyLog(attendedGym = true)
        )
        
        coEvery { repositoryMock.getDailyLogs(any()) } returns flowOf(logs)
        // Re-inject dependencies to process the new mocked flow
        viewModel = MainViewModel(repositoryMock)
        
        testScheduler.runCurrent()
        assertEquals(3, viewModel.gymStreak.value)
    }

    @Test
    fun onDateSelected_updatesSelectedDateTasks() = runTest {
        val date1 = java.time.LocalDate.of(2026, 3, 24)
        val date2 = java.time.LocalDate.of(2026, 3, 25)
        
        val task1 = com.example.irondiary.data.model.Task(
            docId = "t1", 
            description = "Task 1", 
            completed = true,
            completedDate = Timestamp(date1.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().let { java.util.Date.from(it) })
        )
        val task2 = com.example.irondiary.data.model.Task(
            docId = "t2", 
            description = "Task 2", 
            completed = true,
            completedDate = Timestamp(date2.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().let { java.util.Date.from(it) })
        )

        // Inject tasks flow
        coEvery { repositoryMock.getTasks(any()) } returns flowOf(listOf(task1, task2))
        
        // Re-init viewModel to pick up new flow
        viewModel = MainViewModel(repositoryMock)
        
        viewModel.onDateSelected(date1)
        testScheduler.runCurrent()
        assertEquals(1, viewModel.selectedDateTasks.value.size)
        assertEquals("Task 1", viewModel.selectedDateTasks.value[0].description)
        
        viewModel.onDateSelected(date2)
        testScheduler.runCurrent()
        assertEquals(1, viewModel.selectedDateTasks.value.size)
        assertEquals("Task 2", viewModel.selectedDateTasks.value[0].description)
    }

    @Test
    fun onMonthChanged_resetsSelectedDate() = runTest {
        viewModel.onDateSelected(java.time.LocalDate.now())
        assertEquals(java.time.LocalDate.now(), viewModel.selectedDate.value)
        
        viewModel.onMonthChanged()
        assertNull(viewModel.selectedDate.value)
    }
}
