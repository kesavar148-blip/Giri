package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ChatMessageEntity
import com.example.data.model.JarvisMemory
import com.example.data.model.JarvisNote
import com.example.data.model.JarvisTask
import com.example.data.model.UserPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JarvisRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    fun observePreferences(): Flow<UserPreferences?> = flow {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserPreferences::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensurePreferences(displayName: String): UserPreferences {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                existing.toObject(
                    UserPreferences::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserPreferences(id = uid, userId = uid, displayName = displayName.ifBlank { "Commander" })
            } else {
                val cleanName = displayName.trim().ifBlank { "Commander" }.take(80)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "preferredName" to cleanName.split(" ").firstOrNull().orEmpty().take(60),
                    "city" to "San Francisco",
                    "wakeWord" to "Hey Jarvis",
                    "personality" to "butler",
                    "customInstructions" to "",
                    "selectedModel" to "gemini-3.8-flash",
                    "memoryEnabled" to true,
                    "autoSpeakEnabled" to true,
                    "confirmActionsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
                docRef.get().await().toObject(
                    UserPreferences::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserPreferences(id = uid, userId = uid, displayName = cleanName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updatePreferences(prefs: UserPreferences) {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                ensurePreferences(prefs.displayName)
            }
            val validPersonality = prefs.personality.takeIf {
                it in listOf("butler", "tactical", "friendly", "architect")
            } ?: "butler"
            val validModel = prefs.selectedModel.takeIf {
                it in listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon")
            } ?: "gemini-3.8-flash"
            val updates = mapOf<String, Any>(
                "displayName" to prefs.displayName.trim().ifBlank { "Commander" }.take(80),
                "preferredName" to prefs.preferredName.trim().take(60),
                "city" to prefs.city.trim().take(80),
                "wakeWord" to prefs.wakeWord.trim().ifBlank { "Hey Jarvis" }.take(40),
                "personality" to validPersonality,
                "customInstructions" to prefs.customInstructions.trim().take(500),
                "selectedModel" to validModel,
                "memoryEnabled" to prefs.memoryEnabled,
                "autoSpeakEnabled" to prefs.autoSpeakEnabled,
                "confirmActionsEnabled" to prefs.confirmActionsEnabled,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeMessages(): Flow<List<ChatMessageEntity>> = flow {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection("messages")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessageEntity::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun addMessage(
        role: String,
        content: String,
        attachmentLabel: String = "",
        modelUsed: String = "gemini-3.8-flash",
    ): String {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection("messages")
        val docRef = colRef.document()
        val sanitizedContent = content.trim().ifBlank { "(empty)" }.take(15000)
        val payload = mapOf(
            "userId" to uid,
            "role" to if (role == "assistant") "assistant" else "user",
            "content" to sanitizedContent,
            "attachmentLabel" to attachmentLabel.trim().take(120),
            "modelUsed" to modelUsed.trim().take(60),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun clearConversationHistory() {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection("messages")
        try {
            val docs = colRef.get().await().documents
            if (docs.isEmpty()) return
            docs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            throw e
        }
    }

    fun observeTasks(): Flow<List<JarvisTask>> = flow {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection("tasks")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        JarvisTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun addTask(
        title: String,
        notes: String = "",
        priority: String = "medium",
        dueLabel: String = "",
    ): String {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("tasks").document()
        val validPriority = priority.lowercase().takeIf { it in listOf("low", "medium", "high") } ?: "medium"
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(200),
            "notes" to notes.trim().take(1000),
            "priority" to validPriority,
            "dueLabel" to dueLabel.trim().take(80),
            "completed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleTaskCompleted(task: JarvisTask) {
        val uid = requireUserId()
        require(task.id.isNotBlank()) { "Task ID must not be blank" }
        val docRef = userDoc(uid).collection("tasks").document(task.id)
        try {
            docRef.update(
                mapOf(
                    "completed" to !task.completed,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTask(taskId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("tasks").document(taskId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeNotes(): Flow<List<JarvisNote>> = flow {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection("notes")
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        JarvisNote::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun addNote(title: String, content: String, tag: String = "general"): String {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("notes").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(160),
            "content" to content.trim().take(10000),
            "tag" to tag.trim().take(40),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteNote(noteId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("notes").document(noteId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeMemories(): Flow<List<JarvisMemory>> = flow {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection("memories")
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        JarvisMemory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveMemory(key: String, value: String): String {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("memories").document()
        val payload = mapOf(
            "userId" to uid,
            "key" to key.trim().take(80),
            "value" to value.trim().take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteMemory(memoryId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("memories").document(memoryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
