package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class JarvisRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun preferences_createAndUpdate_succeedsForOwner() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = JarvisRepository(firestore)

        val prefs = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensurePreferences("Tony Stark")
        }
        assertEquals("Tony Stark", prefs.displayName)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updatePreferences(prefs.copy(wakeWord = "Jarvis", personality = "tactical"))
        }

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePreferences().first { it?.wakeWord == "Jarvis" }
        }
        assertNotNull(observed)
        assertEquals("tactical", observed?.personality)
    }

    @Test
    fun tasksNotesMessagesMemories_crudAndObserve_succeedsForOwner() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = JarvisRepository(firestore)

        val taskTitle = "Calibration-${UUID.randomUUID()}"
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addTask(title = taskTitle, notes = "Check sensors", priority = "high", dueLabel = "Tonight")
        }
        assertTrue(taskId.isNotBlank())

        val tasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks().first { list -> list.any { it.id == taskId } }
        }
        val createdTask = tasks.first { it.id == taskId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.toggleTaskCompleted(createdTask)
        }
        val toggledTasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks().first { list -> list.any { it.id == taskId && it.completed } }
        }
        assertTrue(toggledTasks.any { it.id == taskId && it.completed })

        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addNote(title = "Suit Note", content = "Titanium gold alloy", tag = "armor")
        }
        val notes = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeNotes().first { list -> list.any { it.id == noteId } }
        }
        assertTrue(notes.any { it.id == noteId })

        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addMessage(role = "user", content = "Hello JARVIS")
        }
        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMessages().first { list -> list.any { it.id == msgId } }
        }
        assertTrue(messages.any { it.id == msgId })

        val memId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveMemory(key = "tea", value = "Earl Grey")
        }
        val memories = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMemories().first { list -> list.any { it.id == memId } }
        }
        assertTrue(memories.any { it.id == memId })
    }

    @Test
    fun crossUserRead_aliceTaskReadByBob_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = JarvisRepository(firestore)
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addTask(title = "Secret Blueprint")
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("tasks").document(taskId).get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's task")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    @Test
    fun unauthenticatedRead_failsWithPermissionDeniedOrAuthCheck() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = JarvisRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addTask(title = "Private Task")
        }

        auth.signOut()

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("tasks").get().await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.jarvis@test.com"
        const val BOB_EMAIL = "bob.jarvis@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
