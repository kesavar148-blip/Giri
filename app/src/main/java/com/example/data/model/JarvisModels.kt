package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserPreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Commander",
    val preferredName: String = "",
    val city: String = "San Francisco",
    val wakeWord: String = "Hey Jarvis",
    val personality: String = "butler",
    val customInstructions: String = "",
    val selectedModel: String = "gemini-3.8-flash",
    val memoryEnabled: Boolean = true,
    val autoSpeakEnabled: Boolean = true,
    val confirmActionsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatMessageEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val content: String = "",
    val attachmentLabel: String = "",
    val modelUsed: String = "gemini-3.8-flash",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class JarvisTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String = "",
    val priority: String = "medium",
    val dueLabel: String = "",
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class JarvisNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val tag: String = "general",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class JarvisMemory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val key: String = "",
    val value: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
