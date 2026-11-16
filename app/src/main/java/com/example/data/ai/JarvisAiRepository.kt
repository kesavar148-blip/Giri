package com.example.data.ai

import android.graphics.Bitmap
import com.example.data.model.ChatMessageEntity
import com.example.data.model.JarvisMemory
import com.example.data.model.UserPreferences
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface JarvisActionProposal {
    val id: String
    val summary: String

    data class CreateTask(
        override val id: String,
        override val summary: String,
        val title: String,
        val priority: String = "medium",
        val dueLabel: String = "",
    ) : JarvisActionProposal

    data class SaveNote(
        override val id: String,
        override val summary: String,
        val title: String,
        val content: String,
        val tag: String = "general",
    ) : JarvisActionProposal

    data class RememberPreference(
        override val id: String,
        override val summary: String,
        val key: String,
        val value: String,
    ) : JarvisActionProposal

    data class OpenUrl(
        override val id: String,
        override val summary: String,
        val url: String,
    ) : JarvisActionProposal

    data class DraftEmail(
        override val id: String,
        override val summary: String,
        val recipient: String,
        val subject: String,
        val body: String,
    ) : JarvisActionProposal

    data class SetAlarm(
        override val id: String,
        override val summary: String,
        val hour: Int,
        val minute: Int,
        val label: String,
    ) : JarvisActionProposal

    data class CreateCalendarEvent(
        override val id: String,
        override val summary: String,
        val title: String,
        val description: String,
    ) : JarvisActionProposal
}

data class ParsedJarvisResponse(
    val displayText: String,
    val proposedActions: List<JarvisActionProposal>,
)

class JarvisAiRepository {

    private var cachedKey: String = ""
    private var cachedModel: GenerativeModel? = null

    private fun getOrCreateModel(
        modelName: String,
        systemPrompt: String,
    ): GenerativeModel {
        val safeModelName = modelName.takeIf {
            it in listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon")
        } ?: "gemini-3.8-flash"
        val key = "$safeModelName|${systemPrompt.hashCode()}"
        val existing = cachedModel
        if (existing != null && cachedKey == key) {
            return existing
        }
        val model = Firebase.ai.generativeModel(
            modelName = safeModelName,
            generationConfig = generationConfig {
                temperature = 0.7f
            },
            systemInstruction = content {
                text(systemPrompt)
            },
        )
        cachedKey = key
        cachedModel = model
        return model
    }

    fun buildSystemPrompt(
        prefs: UserPreferences,
        memories: List<JarvisMemory>,
    ): String {
        val nowFormatted = SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm a z", Locale.getDefault())
            .format(Date())
        val personaDesc = when (prefs.personality) {
            "tactical" -> "You are JARVIS in Tactical HUD mode: ultra-concise, mission-focused, telemetry-style clarity, minimal fluff."
            "friendly" -> "You are JARVIS in Warm Companion mode: approachable, encouraging, conversational, and helpful."
            "architect" -> "You are JARVIS in Senior Architect mode: deeply technical, structured, engineering-first explanations and clean code."
            else -> "You are JARVIS, inspired by Iron Man's intelligent butler-AI: articulate, composed, subtly witty, respectful (addressing the user respectfully when natural), and razor-sharp."
        }
        val memorySection = if (prefs.memoryEnabled && memories.isNotEmpty()) {
            buildString {
                appendLine("USER-APPROVED MEMORIES:")
                memories.take(15).forEach { mem ->
                    appendLine("- ${mem.key}: ${mem.value}")
                }
            }
        } else if (!prefs.memoryEnabled) {
            "Memory storage is currently DISABLED by user privacy settings. Do not propose saving memories."
        } else {
            "No saved memories yet."
        }

        val nameLine = prefs.preferredName.takeIf { it.isNotBlank() }?.let {
            "Preferred user name: $it."
        } ?: "User display name: ${prefs.displayName}."

        val customLine = prefs.customInstructions.takeIf { it.isNotBlank() }?.let {
            "Custom user instructions: $it"
        } ?: ""

        return """
            $personaDesc
            Current local time: $nowFormatted
            Default location city: ${prefs.city.ifBlank { "Unknown" }}
            $nameLine
            $customLine
            $memorySection

            CAPABILITIES & ACTION PROTOCOL:
            - You help with Q&A, explanations, math, language translation, emails/letters, summarization, code generation, weather/news/web synthesis, document & image/camera vision analysis, and personal organization.
            - If live web/weather/news context is provided in the prompt, use it accurately and cite sources naturally.
            - When the user explicitly asks to perform an assistant action (add a to-do/reminder, save a note, remember a preference if memory is enabled, open a website, draft an email, set an alarm, or schedule a calendar event), append a single-line action tag at the END of your reply using this exact format:
              [[JARVIS_ACTION:{"type":"create_task","title":"...","priority":"medium","dueLabel":"..."}]]
              Supported action JSON types:
              1) {"type":"create_task","title":"...","priority":"low|medium|high","dueLabel":"..."}
              2) {"type":"save_note","title":"...","content":"...","tag":"..."}
              3) {"type":"remember_pref","key":"...","value":"..."}
              4) {"type":"open_url","url":"https://...","label":"..."}
              5) {"type":"draft_email","recipient":"...","subject":"...","body":"..."}
              6) {"type":"set_alarm","hour":7,"minute":30,"label":"..."}
              7) {"type":"calendar_event","title":"...","description":"..."}
            - Only emit [[JARVIS_ACTION:...]] when an action is clearly requested or directly helpful; keep the conversational text clean and helpful before the tag.
        """.trimIndent()
    }

    fun streamChatReply(
        prompt: String,
        history: List<ChatMessageEntity>,
        prefs: UserPreferences,
        memories: List<JarvisMemory>,
        liveContext: String = "",
        attachedBitmap: Bitmap? = null,
        attachedDocumentText: String = "",
    ): Flow<String> = flow {
        val systemPrompt = buildSystemPrompt(prefs, memories)
        val model = getOrCreateModel(prefs.selectedModel, systemPrompt)

        val recentHistory = history.takeLast(12).map { msg ->
            content(role = if (msg.role == "assistant") "model" else "user") {
                text(msg.content)
            }
        }

        val fullUserPrompt = buildString {
            if (liveContext.isNotBlank()) {
                appendLine("[LIVE REAL-TIME TELEMETRY & WEB CONTEXT]")
                appendLine(liveContext)
                appendLine("[END CONTEXT]")
                appendLine()
            }
            if (attachedDocumentText.isNotBlank()) {
                appendLine("[ATTACHED DOCUMENT CONTENT]")
                appendLine(attachedDocumentText.take(12000))
                appendLine("[END DOCUMENT]")
                appendLine()
            }
            append(prompt)
        }

        val userContent = content("user") {
            if (attachedBitmap != null) {
                image(attachedBitmap)
            }
            text(fullUserPrompt)
        }

        val contentsToSend = recentHistory + userContent
        val accumulated = StringBuilder()

        try {
            withTimeout(60_000L) {
                model.generateContentStream(contentsToSend).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("JARVIS returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun parseActionsFromResponse(rawText: String): ParsedJarvisResponse {
        val regex = Regex("""\[\[JARVIS_ACTION:(\{.*?\})\]\]""")
        val proposals = mutableListOf<JarvisActionProposal>()
        var idx = 0
        for (match in regex.findAll(rawText)) {
            val jsonStr = match.groupValues.getOrNull(1) ?: continue
            runCatching {
                val obj = JSONObject(jsonStr)
                val id = "act_${System.currentTimeMillis()}_${idx++}"
                when (obj.optString("type")) {
                    "create_task" -> {
                        val title = obj.optString("title").trim()
                        if (title.isNotBlank()) {
                            proposals.add(
                                JarvisActionProposal.CreateTask(
                                    id = id,
                                    summary = "Add Task: $title",
                                    title = title,
                                    priority = obj.optString("priority", "medium"),
                                    dueLabel = obj.optString("dueLabel", ""),
                                )
                            )
                        }
                    }
                    "save_note" -> {
                        val title = obj.optString("title").trim()
                        val content = obj.optString("content").trim()
                        if (title.isNotBlank() && content.isNotBlank()) {
                            proposals.add(
                                JarvisActionProposal.SaveNote(
                                    id = id,
                                    summary = "Save Note: $title",
                                    title = title,
                                    content = content,
                                    tag = obj.optString("tag", "jarvis"),
                                )
                            )
                        }
                    }
                    "remember_pref" -> {
                        val key = obj.optString("key").trim()
                        val value = obj.optString("value").trim()
                        if (key.isNotBlank() && value.isNotBlank()) {
                            proposals.add(
                                JarvisActionProposal.RememberPreference(
                                    id = id,
                                    summary = "Remember '$key': $value",
                                    key = key,
                                    value = value,
                                )
                            )
                        }
                    }
                    "open_url" -> {
                        val url = obj.optString("url").trim()
                        val label = obj.optString("label", url).trim()
                        if (url.isNotBlank()) {
                            proposals.add(
                                JarvisActionProposal.OpenUrl(
                                    id = id,
                                    summary = "Open Website: $label",
                                    url = url,
                                )
                            )
                        }
                    }
                    "draft_email" -> {
                        val subject = obj.optString("subject").trim()
                        val body = obj.optString("body").trim()
                        val recipient = obj.optString("recipient").trim()
                        proposals.add(
                            JarvisActionProposal.DraftEmail(
                                id = id,
                                summary = "Open Email Draft: ${subject.ifBlank { "New Message" }}",
                                recipient = recipient,
                                subject = subject,
                                body = body,
                            )
                        )
                    }
                    "set_alarm" -> {
                        val hour = obj.optInt("hour", 7)
                        val minute = obj.optInt("minute", 0)
                        val label = obj.optString("label", "JARVIS Alarm")
                        proposals.add(
                            JarvisActionProposal.SetAlarm(
                                id = id,
                                summary = "Set Alarm %02d:%02d (%s)".format(hour, minute, label),
                                hour = hour,
                                minute = minute,
                                label = label,
                            )
                        )
                    }
                    "calendar_event" -> {
                        val title = obj.optString("title", "JARVIS Event")
                        val desc = obj.optString("description", "")
                        proposals.add(
                            JarvisActionProposal.CreateCalendarEvent(
                                id = id,
                                summary = "Create Calendar Event: $title",
                                title = title,
                                description = desc,
                            )
                        )
                    }
                }
            }
        }
        val cleanText = rawText.replace(regex, "").trim()
        return ParsedJarvisResponse(
            displayText = cleanText,
            proposedActions = proposals,
        )
    }
}
