package com.example.irondiary.viewmodel

import com.example.irondiary.data.Resource
import com.example.irondiary.data.repository.IronDiaryRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateStressTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel
    private val repository = mockk<IronDiaryRepository>(relaxed = true)
    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } answers { 0 }
        every { Log.e(any<String>(), any<String>()) } answers { 0 }
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } answers { 0 }
        every { Log.w(any<String>(), any<String>()) } answers { 0 }
        
        mockkStatic(FirebaseAuth::class)
        val auth = mockk<FirebaseAuth>(relaxed = true)
        every { FirebaseAuth.getInstance() } returns auth
        
        every { repository.context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        
        // Initial load with empty templates
        every { sharedPrefs.getString("task_templates", "[]") } returns "[]"
        
        viewModel = MainViewModel(repository, testDispatcher)
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseAuth::class)
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun addTemplate_respectsLimit_at100() = runTest {
        // Fill up to 100
        for (i in 1..100) {
            viewModel.addTemplate("Task $i")
        }
        
        // Try adding 101st
        viewModel.addTemplate("Task 101")
        
        val status = viewModel.templateStatus.value
        assertTrue(status is Resource.Error)
        assertEquals("Maximum of 100 custom templates allowed.", (status as Resource.Error).message)
    }

    @Test
    fun addTemplate_preventsDuplicates_caseInsensitive() = runTest {
        viewModel.addTemplate("Drink Water")
        viewModel.addTemplate("drink water")
        
        val status = viewModel.templateStatus.value
        assertTrue(status is Resource.Error)
        assertEquals("Template 'drink water' already exists.", (status as Resource.Error).message)
    }

    @Test
    fun loadTemplates_handlesCorruptedJson_gracefully() = runTest {
        // Mock a corrupted JSON array where some items are objects, some are invalid strings
        val corruptedJson = """
            [
                {"title": "Valid One", "emoji": "✅"},
                "Legacy String Template",
                {"broken": "object"},
                null,
                123
            ]
        """.trimIndent()
        
        every { sharedPrefs.getString("task_templates", "[]") } returns corruptedJson
        
        // Use a new ViewModel instance to trigger loadTemplates
        val vm = MainViewModel(repository, testDispatcher)
        
        val custom = vm.categorizedTemplates.value["Custom"] ?: emptyList()
        
        // Should contain "Valid One" and "Legacy String Template"
        // Objects with missing fields get defaults ("Unknown Task", "✨")
        // Non-object/string entries are skipped
        assertTrue(custom.any { it.title == "Valid One" })
        assertTrue(custom.any { it.title == "Legacy String Template" })
        assertTrue(custom.any { it.title == "Unknown Task" }) // from the {"broken": "object"}
        
        // Total should be around 3 (Valid, Legacy, BrokenObject)
        assertEquals(3, custom.size)
    }

    @Test
    fun emojiValidation_allowsMultiByte_butCapsLength() = runTest {
        // Some emojis are multiple chars in Java/Kotlin (e.g. skin tones, flags)
        val multiByteEmoji = "🏃🏾‍♂️" 
        
        viewModel.addTemplate("Running", multiByteEmoji)
        
        val status = viewModel.templateStatus.value
        assertTrue("Status should be success but was $status", status is Resource.Success)
        
        // Now try an actual long string
        viewModel.addTemplate("Long Emoji", "ThisIsWayTooLongForAnEmoji")
        val errorStatus = viewModel.templateStatus.value
        assertTrue(errorStatus is Resource.Error)
        assertEquals("Emoji too long.", (errorStatus as Resource.Error).message)
    }
}
