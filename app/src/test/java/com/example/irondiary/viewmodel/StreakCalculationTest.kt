package com.example.irondiary.viewmodel

import androidx.work.WorkManager
import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.repository.IronDiaryRepository
import com.example.irondiary.util.MainDispatcherRule
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class StreakCalculationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repositoryMock: IronDiaryRepository
    private lateinit var viewModel: MainViewModel
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    @Before
    fun setup() {
        mockkStatic(FirebaseAuth::class)
        mockkStatic(WorkManager::class)
        mockkStatic(Log::class)
        
        val authMock = mockk<FirebaseAuth>(relaxed = true)
        val mockUser = mockk<FirebaseUser>(relaxed = true)
        val workManagerMock = mockk<WorkManager>(relaxed = true)
        
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        
        every { FirebaseAuth.getInstance() } returns authMock
        every { mockUser.uid } returns "test_uid"
        every { authMock.currentUser } returns mockUser
        
        every { WorkManager.getInstance(any()) } returns workManagerMock

        repositoryMock = mockk(relaxed = true)
        val applicationMock = mockk<android.app.Application>(relaxed = true)
        val sharedPrefsMock = mockk<android.content.SharedPreferences>(relaxed = true)
        
        every { repositoryMock.context } returns applicationMock
        every { applicationMock.getSharedPreferences(any(), any()) } returns sharedPrefsMock
        every { sharedPrefsMock.getString(any(), any()) } returns "[]"
        
        // Mock default flows
        coEvery { repositoryMock.getTasks(any()) } returns flowOf(emptyList())
        coEvery { repositoryMock.getWeightData(any()) } returns flowOf(emptyList())
        coEvery { repositoryMock.getStudySessions(any()) } returns flowOf(emptyList())
        coEvery { repositoryMock.getDailyLogs(any()) } returns flowOf(emptyMap())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun initViewModelWithLogs(logs: Map<String, DailyLog>) {
        coEvery { repositoryMock.getDailyLogs(any()) } returns flowOf(logs)
        viewModel = MainViewModel(repositoryMock, mainDispatcherRule.testDispatcher)
    }

    @Test
    fun calculateStats_simpleStreak_calculatesCorrectly() = runTest {
        val today = LocalDate.now()
        val logs = mapOf(
            today.format(formatter) to DailyLog(attendedGym = true),
            today.minusDays(1).format(formatter) to DailyLog(attendedGym = true),
            today.minusDays(2).format(formatter) to DailyLog(attendedGym = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        assertEquals(3, viewModel.gymStreak.value)
        assertEquals(3, viewModel.bestStreak.value)
        assertEquals(3, viewModel.totalWorkouts.value)
    }

    @Test
    fun calculateStats_streakWithGap_breaksCorrectly() = runTest {
        val today = LocalDate.now()
        val logs = mapOf(
            today.format(formatter) to DailyLog(attendedGym = true),
            // Missing yesterday
            today.minusDays(2).format(formatter) to DailyLog(attendedGym = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        assertEquals(1, viewModel.gymStreak.value)
        assertEquals(1, viewModel.bestStreak.value)
        assertEquals(2, viewModel.totalWorkouts.value)
    }

    @Test
    fun calculateStats_todayNotLogged_streakContinuesFromYesterday() = runTest {
        val today = LocalDate.now()
        val logs = mapOf(
            today.minusDays(1).format(formatter) to DailyLog(attendedGym = true),
            today.minusDays(2).format(formatter) to DailyLog(attendedGym = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        assertEquals(2, viewModel.gymStreak.value)
    }

    @Test
    fun calculateStats_todayLoggedFalse_streakContinuesFromYesterday() = runTest {
        val today = LocalDate.now()
        val logs = mapOf(
            today.format(formatter) to DailyLog(attendedGym = false),
            today.minusDays(1).format(formatter) to DailyLog(attendedGym = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        // This is the current "friendly" behavior: streak stays alive until end of day
        assertEquals(1, viewModel.gymStreak.value)
    }

    @Test
    fun calculateStats_monthBoundary_calculatesCorrectly() = runTest {
        val march1 = LocalDate.of(2026, 3, 1)
        // Simulate "today" is March 1
        // Since we can't easily mock LocalDate.now() in the VM without refactoring, 
        // we'll assume the VM uses the system clock.
        // To test boundaries, we'll provide logs that cross them.
        
        val logs = mutableMapOf<String, DailyLog>()
        for (i in 0..10) {
            val date = march1.minusDays(i.toLong())
            logs[date.format(formatter)] = DailyLog(attendedGym = true)
        }
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        // If today is after March 1, the streak will be at least 11
        // We just care that it doesn't break at the month boundary (Feb 28 -> March 1)
        // Since we can't control 'today' in the VM, we check if bestStreak is correct
        assertEquals(11, viewModel.bestStreak.value)
    }

    @Test
    fun calculateStats_leapYear_calculatesCorrectly() = runTest {
        // 2024 was a leap year. Feb 28 -> Feb 29 -> March 1
        val logs = mapOf(
            "2024-02-28" to DailyLog(attendedGym = true),
            "2024-02-29" to DailyLog(attendedGym = true),
            "2024-03-01" to DailyLog(attendedGym = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        assertEquals(3, viewModel.bestStreak.value)
    }

    @Test
    fun calculateStats_futureLogs_ignoredInCurrentStreak() = runTest {
        val today = LocalDate.now()
        val logs = mapOf(
            today.format(formatter) to DailyLog(attendedGym = true),
            today.plusDays(1).format(formatter) to DailyLog(attendedGym = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        assertEquals(1, viewModel.gymStreak.value)
        assertEquals(2, viewModel.bestStreak.value) // Best streak counts everything
    }

    @Test
    fun calculateStats_bestStreak_multipleSequences() = runTest {
        val logs = mapOf(
            "2026-01-01" to DailyLog(attendedGym = true),
            "2026-01-02" to DailyLog(attendedGym = true),
            // Gap
            "2026-01-04" to DailyLog(attendedGym = true),
            "2026-01-05" to DailyLog(attendedGym = true),
            "2026-01-06" to DailyLog(attendedGym = true),
            // Gap
            "2026-01-08" to DailyLog(attendedGym = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        assertEquals(3, viewModel.bestStreak.value) // Jan 4-6 is the best
    }

    @Test
    fun calculateStats_performanceStressTest() = runTest {
        val largeLogs = mutableMapOf<String, DailyLog>()
        val startDate = LocalDate.of(2020, 1, 1)
        for (i in 0 until 1000) {
            largeLogs[startDate.plusDays(i.toLong()).format(formatter)] = DailyLog(attendedGym = true)
        }
        
        val startTime = System.currentTimeMillis()
        initViewModelWithLogs(largeLogs)
        advanceUntilIdle()
        val endTime = System.currentTimeMillis()
        
        assertEquals(1000, viewModel.bestStreak.value)
        println("Calculation for 1000 days took ${endTime - startTime}ms")
    }

    @Test
    fun calculateStats_restDay_preservesStreak() = runTest {
        val today = LocalDate.now()
        val logs = mapOf(
            today.format(formatter) to DailyLog(attendedGym = true),
            today.minusDays(1).format(formatter) to DailyLog(isRestDay = true),
            today.minusDays(2).format(formatter) to DailyLog(attendedGym = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        // Sat (T) -> Sun (Rest) -> Mon (T)
        // Current streak should be 2
        assertEquals(2, viewModel.gymStreak.value)
        assertEquals(2, viewModel.bestStreak.value)
    }

    @Test
    fun calculateStats_multipleRestDays_preservesStreak() = runTest {
        val today = LocalDate.now()
        val logs = mapOf(
            today.format(formatter) to DailyLog(attendedGym = true),
            today.minusDays(1).format(formatter) to DailyLog(isRestDay = true),
            today.minusDays(2).format(formatter) to DailyLog(isRestDay = true),
            today.minusDays(3).format(formatter) to DailyLog(attendedGym = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        assertEquals(2, viewModel.gymStreak.value)
    }

    @Test
    fun calculateStats_restDayOnToday_preservesStreakFromYesterday() = runTest {
        val today = LocalDate.now()
        val logs = mapOf(
            today.format(formatter) to DailyLog(isRestDay = true),
            today.minusDays(1).format(formatter) to DailyLog(attendedGym = true),
            today.minusDays(2).format(formatter) to DailyLog(attendedGym = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        // If today is a rest day, and yesterday was a 2-day streak, it should show 2.
        assertEquals(2, viewModel.gymStreak.value)
    }

    @Test
    fun calculateStats_startOfHistoryIsRestDay_streakStartsFromFirstGymDay() = runTest {
        val today = LocalDate.now()
        val logs = mapOf(
            today.format(formatter) to DailyLog(attendedGym = true),
            today.minusDays(1).format(formatter) to DailyLog(isRestDay = true)
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        assertEquals(1, viewModel.gymStreak.value)
    }

    @Test
    fun calculateStats_bestStreak_withRestDays_calculatesCorrectMax() = runTest {
        val logs = mapOf(
            "2026-01-01" to DailyLog(attendedGym = true),
            "2026-01-02" to DailyLog(isRestDay = true),
            "2026-01-03" to DailyLog(attendedGym = true), // Streak of 2
            "2026-01-04" to DailyLog(attendedGym = false), // Break
            "2026-01-05" to DailyLog(attendedGym = true),
            "2026-01-06" to DailyLog(attendedGym = true),
            "2026-01-07" to DailyLog(attendedGym = true) // Streak of 3
        )
        
        initViewModelWithLogs(logs)
        advanceUntilIdle()
        
        assertEquals(3, viewModel.bestStreak.value)
    }
}
