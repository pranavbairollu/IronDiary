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
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.example.irondiary.data.repository.IronDiaryRepository
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authMock: FirebaseAuth
    private lateinit var firestoreMock: FirebaseFirestore
    private lateinit var applicationMock: Application
    private lateinit var prefsMock: SharedPreferences
    private lateinit var viewModel: MainViewModel

    private lateinit var userDocMock: DocumentReference
    private lateinit var tasksCollectionMock: CollectionReference
    private lateinit var studyCollectionMock: CollectionReference
    private lateinit var logsCollectionMock: CollectionReference

    @Before
    fun setup() {
        mockkStatic(FirebaseAuth::class)
        mockkStatic(FirebaseFirestore::class)

        authMock = mockk(relaxed = true)
        firestoreMock = mockk(relaxed = true)
        applicationMock = mockk(relaxed = true)
        prefsMock = mockk(relaxed = true)

        every { FirebaseAuth.getInstance() } returns authMock
        every { FirebaseFirestore.getInstance() } returns firestoreMock
        every { applicationMock.getSharedPreferences(any(), any()) } returns prefsMock

        mockkConstructor(IronDiaryRepository::class)
        coEvery { anyConstructed<IronDiaryRepository>().getTasks(any()) } returns flowOf(emptyList())
        coEvery { anyConstructed<IronDiaryRepository>().addTask(any(), any()) } returns Unit
        coEvery { anyConstructed<IronDiaryRepository>().updateTask(any(), any()) } returns Unit
        coEvery { anyConstructed<IronDiaryRepository>().deleteTask(any(), any()) } returns Unit

        val mockUser = mockk<FirebaseUser>(relaxed = true)
        every { mockUser.uid } returns "test_uid"
        every { authMock.currentUser } returns mockUser

        // Mock Firestore chain: firestore.collection("users").document("test_uid")
        val rootCollectionMock = mockk<CollectionReference>(relaxed = true)
        userDocMock = mockk<DocumentReference>(relaxed = true)
        tasksCollectionMock = mockk<CollectionReference>(relaxed = true)
        studyCollectionMock = mockk<CollectionReference>(relaxed = true)
        logsCollectionMock = mockk<CollectionReference>(relaxed = true)

        every { firestoreMock.collection("users") } returns rootCollectionMock
        every { rootCollectionMock.document("test_uid") } returns userDocMock
        every { userDocMock.collection("tasks") } returns tasksCollectionMock
        every { userDocMock.collection("study_sessions") } returns studyCollectionMock
        every { userDocMock.collection("daily_logs") } returns logsCollectionMock

        // Initialize viewModel
        viewModel = MainViewModel(applicationMock)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun addTask_blankInput_emitsErrorWithoutFirebaseCall() = runTest {
        viewModel.saveStatus.test {
            assertNull(awaitItem()) // initial state
            
            viewModel.addTask("   ")
            
            val errorState = awaitItem()
            assertTrue(errorState is Resource.Error)
            assertEquals("Task description cannot be empty.", (errorState as Resource.Error).message)
            
            verify(exactly = 0) { tasksCollectionMock.add(any()) }
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
            
            verify(exactly = 0) { tasksCollectionMock.add(any()) }
        }
    }

    @Test
    fun addTask_validInput_emitsSuccess() = runTest {
        viewModel.saveStatus.test {
            assertNull(awaitItem()) // Initial

            viewModel.addTask("Read physics textbook")

            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Success(Unit), awaitItem())
            
            // Verify our repository intercepted the write
            coVerify(exactly = 1) { anyConstructed<IronDiaryRepository>().addTask(any(), "test_uid") }
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
            
            verify(exactly = 0) { studyCollectionMock.add(any()) }
        }
    }

    @Test
    fun addStudySession_invalidDuration_emitsErrorWithoutFirebaseCall() = runTest {
        viewModel.saveStatus.test {
            assertNull(awaitItem())
            
            // Test 0 duration
            viewModel.addStudySession("Math", 0f)
            assertEquals("Duration must be between 0 and 24 hours.", (awaitItem() as Resource.Error).message)
            
            // Test excessive duration
            viewModel.resetSaveStatus()
            assertNull(awaitItem())
            viewModel.addStudySession("Math", 25f)
            assertEquals("Duration must be between 0 and 24 hours.", (awaitItem() as Resource.Error).message)
            
            verify(exactly = 0) { studyCollectionMock.add(any()) }
        }
    }

    @Test
    fun addStudySession_validInput_emitsSuccess() = runTest {
        val mockDocRef = mockk<DocumentReference>(relaxed = true)
        val mockTask = mockk<Task<DocumentReference>>()
        
        every { studyCollectionMock.add(any()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } answers {
            val listener = arg<OnSuccessListener<DocumentReference>>(0)
            listener.onSuccess(mockDocRef)
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } answers { mockTask }

        viewModel.saveStatus.test {
            assertNull(awaitItem())

            viewModel.addStudySession("Computer Science", 2.5f)

            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Success(Unit), awaitItem())
        }
    }

    @Test
    fun saveFailed_networkError_emitsError() = runTest {
        val exception = Exception("Network offline")
        coEvery { anyConstructed<IronDiaryRepository>().addTask(any(), any()) } throws exception

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
        // Explicitly simulate logged out state
        every { authMock.currentUser } returns null

        viewModel.saveStatus.test {
            assertNull(awaitItem())

            viewModel.addTask("Task without user")

            val errorState = awaitItem()
            assertTrue(errorState is Resource.Error)
            assertEquals("Must be logged in.", (errorState as Resource.Error).message)
            
            coVerify(exactly = 0) { anyConstructed<IronDiaryRepository>().addTask(any(), any()) }
        }
    }
}
