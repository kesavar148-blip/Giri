package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.JarvisActionProposal
import com.example.data.ai.JarvisAiRepository
import com.example.data.model.ChatMessageEntity
import com.example.data.model.JarvisMemory
import com.example.data.model.JarvisNote
import com.example.data.model.JarvisTask
import com.example.data.model.UserPreferences
import com.example.data.repository.JarvisRepository
import com.example.data.tools.JarvisToolsRepository
import com.example.data.tools.NewsArticle
import com.example.data.tools.SearchHit
import com.example.data.tools.WeatherReport
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiDataState<out T> {
    data object Loading : UiDataState<Nothing>
    data class Success<T>(val data: T) : UiDataState<T>
    data class Error(val message: String) : UiDataState<Nothing>
}

data class PendingAttachment(
    val label: String,
    val bitmap: Bitmap? = null,
    val documentText: String = "",
)

class JarvisViewModel(
    private val repository: JarvisRepository,
    private val aiRepository: JarvisAiRepository,
    private val toolsRepository: JarvisToolsRepository,
    private val currentUserId: String,
    private val initialDisplayName: String,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.observePreferences()
        .map { it ?: UserPreferences(id = currentUserId, userId = currentUserId, displayName = initialDisplayName.ifBlank { "Commander" }) }
        .catch { err ->
            val root = unwrapFirestoreError(err)
            if (root == null && err is CancellationException) throw err
            Log.w(TAG, "Preferences observation failed", root ?: err)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences(id = currentUserId, userId = currentUserId, displayName = initialDisplayName.ifBlank { "Commander" }),
        )

    val messagesState: StateFlow<UiDataState<List<ChatMessageEntity>>> = repository.observeMessages()
        .map<List<ChatMessageEntity>, UiDataState<List<ChatMessageEntity>>> { UiDataState.Success(it) }
        .catch { err ->
            val root = unwrapFirestoreError(err)
            if (root == null && err is CancellationException) throw err
            val msg = root?.message ?: err.message ?: "Failed to load conversation history."
            Log.w(TAG, "Error observing messages", root ?: err)
            emit(UiDataState.Error(msg))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiDataState.Loading,
        )

    val tasksState: StateFlow<UiDataState<List<JarvisTask>>> = repository.observeTasks()
        .map<List<JarvisTask>, UiDataState<List<JarvisTask>>> { UiDataState.Success(it) }
        .catch { err ->
            val root = unwrapFirestoreError(err)
            if (root == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing tasks", root ?: err)
            emit(UiDataState.Error(root?.message ?: err.message ?: "Failed to load tasks."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiDataState.Loading,
        )

    val notesState: StateFlow<UiDataState<List<JarvisNote>>> = repository.observeNotes()
        .map<List<JarvisNote>, UiDataState<List<JarvisNote>>> { UiDataState.Success(it) }
        .catch { err ->
            val root = unwrapFirestoreError(err)
            if (root == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing notes", root ?: err)
            emit(UiDataState.Error(root?.message ?: err.message ?: "Failed to load notes."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiDataState.Loading,
        )

    val memoriesState: StateFlow<UiDataState<List<JarvisMemory>>> = repository.observeMemories()
        .map<List<JarvisMemory>, UiDataState<List<JarvisMemory>>> { UiDataState.Success(it) }
        .catch { err ->
            val root = unwrapFirestoreError(err)
            if (root == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing memories", root ?: err)
            emit(UiDataState.Error(root?.message ?: err.message ?: "Failed to load memories."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiDataState.Loading,
        )

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _streamingReply = MutableStateFlow("")
    val streamingReply: StateFlow<String> = _streamingReply.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    private val _pendingActions = MutableStateFlow<List<JarvisActionProposal>>(emptyList())
    val pendingActions: StateFlow<List<JarvisActionProposal>> = _pendingActions.asStateFlow()

    private val _lastSearchHits = MutableStateFlow<List<SearchHit>>(emptyList())
    val lastSearchHits: StateFlow<List<SearchHit>> = _lastSearchHits.asStateFlow()

    private val _weatherReport = MutableStateFlow<WeatherReport?>(null)
    val weatherReport: StateFlow<WeatherReport?> = _weatherReport.asStateFlow()

    private val _weatherLoading = MutableStateFlow(false)
    val weatherLoading: StateFlow<Boolean> = _weatherLoading.asStateFlow()

    private val _weatherError = MutableStateFlow<String?>(null)
    val weatherError: StateFlow<String?> = _weatherError.asStateFlow()

    private val _newsArticles = MutableStateFlow<List<NewsArticle>>(emptyList())
    val newsArticles: StateFlow<List<NewsArticle>> = _newsArticles.asStateFlow()

    private val _newsLoading = MutableStateFlow(false)
    val newsLoading: StateFlow<Boolean> = _newsLoading.asStateFlow()

    private val _webSearchLoading = MutableStateFlow(false)
    val webSearchLoading: StateFlow<Boolean> = _webSearchLoading.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.ensurePreferences(initialDisplayName) }
                .onFailure { Log.w(TAG, "Initial preferences sync warning", it) }
        }
        refreshWeather("San Francisco")
        refreshNews("")
    }

    fun attachImage(bitmap: Bitmap, label: String = "Image attachment") {
        _pendingAttachment.value = PendingAttachment(label = label, bitmap = bitmap)
    }

    fun attachDocument(label: String, content: String) {
        _pendingAttachment.value = PendingAttachment(label = label, documentText = content)
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun dismissError() {
        _chatError.value = null
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun sendPrompt(
        rawPrompt: String,
        includeLiveWebSearch: Boolean = false,
        onAssistantFinished: ((String) -> Unit)? = null,
    ) {
        val prompt = rawPrompt.trim()
        val attachment = _pendingAttachment.value
        if (prompt.isBlank() && attachment == null) return
        if (_isThinking.value) return

        val finalPrompt = prompt.ifBlank { "Please analyze the attached item." }

        viewModelScope.launch {
            _isThinking.value = true
            _chatError.value = null
            _streamingReply.value = ""
            _pendingAttachment.value = null

            val prefs = preferences.value
            val currentMessages = (messagesState.value as? UiDataState.Success)?.data.orEmpty()
            val memories = if (prefs.memoryEnabled) {
                (memoriesState.value as? UiDataState.Success)?.data.orEmpty()
            } else {
                emptyList()
            }

            try {
                repository.addMessage(
                    role = "user",
                    content = finalPrompt,
                    attachmentLabel = attachment?.label.orEmpty(),
                    modelUsed = prefs.selectedModel,
                )

                val liveContext = buildLiveContextIfNeeded(
                    prompt = finalPrompt,
                    city = prefs.city,
                    forceSearch = includeLiveWebSearch,
                )

                val rawBuffer = StringBuilder()
                aiRepository.streamChatReply(
                    prompt = finalPrompt,
                    history = currentMessages,
                    prefs = prefs,
                    memories = memories,
                    liveContext = liveContext,
                    attachedBitmap = attachment?.bitmap,
                    attachedDocumentText = attachment?.documentText.orEmpty(),
                ).collect { partial ->
                    rawBuffer.clear()
                    rawBuffer.append(partial)
                    val cleanedPreview = partial.replace(Regex("""\[\[JARVIS_ACTION:.*$"""), "").trim()
                    _streamingReply.value = cleanedPreview
                }

                val parsed = aiRepository.parseActionsFromResponse(rawBuffer.toString())
                val replyText = parsed.displayText.ifBlank { "Command acknowledged." }

                repository.addMessage(
                    role = "assistant",
                    content = replyText,
                    attachmentLabel = "",
                    modelUsed = prefs.selectedModel,
                )

                if (parsed.proposedActions.isNotEmpty()) {
                    if (prefs.confirmActionsEnabled) {
                        _pendingActions.value = _pendingActions.value + parsed.proposedActions
                    } else {
                        parsed.proposedActions.forEach { action ->
                            executeActionWithoutContext(action)
                        }
                    }
                }

                if (prefs.autoSpeakEnabled) {
                    onAssistantFinished?.invoke(replyText)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out", e)
                _chatError.value = "JARVIS response timed out after 60 seconds. Please try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "JARVIS inference failed", e)
                _chatError.value = e.localizedMessage ?: "Failed to generate JARVIS response."
            } finally {
                _streamingReply.value = ""
                _isThinking.value = false
            }
        }
    }

    private suspend fun buildLiveContextIfNeeded(
        prompt: String,
        city: String,
        forceSearch: Boolean,
    ): String {
        val lower = prompt.lowercase()
        val parts = mutableListOf<String>()

        if (lower.contains("weather") || lower.contains("temperature") || lower.contains("forecast")) {
            val targetCity = extractCityFromPrompt(prompt).ifBlank { city.ifBlank { "San Francisco" } }
            toolsRepository.fetchWeatherByCity(targetCity).onSuccess { w ->
                _weatherReport.value = w
                parts.add(
                    "Live Weather (${w.city}, ${w.country}): ${w.temperatureC}°C (feels like ${w.apparentTempC}°C), ${w.conditionLabel}, High ${w.highC}°C / Low ${w.lowC}°C, Humidity ${w.humidityPercent}%, Wind ${w.windKmh} km/h."
                )
            }
        }

        if (lower.contains("news") || lower.contains("headlines") || lower.contains("latest updates")) {
            toolsRepository.fetchLatestNews("").onSuccess { articles ->
                _newsArticles.value = articles
                val headlineList = articles.take(5).joinToString("\n") { "- ${it.title} (${it.source})" }
                parts.add("Latest Live News Headlines:\n$headlineList")
            }
        }

        if (forceSearch || lower.startsWith("search ") || lower.contains("look up ") || lower.contains("current ") || lower.contains("who is ") || lower.contains("latest ")) {
            val query = prompt.removePrefix("search ").removePrefix("Search ").trim()
            toolsRepository.searchWeb(query).onSuccess { hits ->
                _lastSearchHits.value = hits
                if (hits.isNotEmpty()) {
                    val summary = hits.take(4).joinToString("\n") { "- [${it.source}] ${it.title}: ${it.snippet} (${it.url})" }
                    parts.add("Live Web Search Results:\n$summary")
                }
            }
        }

        if (lower.contains("time") || lower.contains("date") || lower.contains("day is it")) {
            val formatted = SimpleDateFormat("EEEE, MMMM d, yyyy h:mm:ss a z", Locale.getDefault()).format(Date())
            parts.add("Device Clock Telemetry: $formatted")
        }

        return parts.joinToString("\n\n")
    }

    private fun extractCityFromPrompt(prompt: String): String {
        val match = Regex("""weather\s+(?:in|for|at)\s+([A-Za-z\s,]+)""", RegexOption.IGNORE_CASE).find(prompt)
        return match?.groupValues?.getOrNull(1)?.trim()?.take(40).orEmpty()
    }

    fun approveAction(action: JarvisActionProposal, context: Context) {
        viewModelScope.launch {
            _pendingActions.value = _pendingActions.value.filterNot { it.id == action.id }
            when (action) {
                is JarvisActionProposal.CreateTask -> {
                    runCatching {
                        repository.addTask(action.title, priority = action.priority, dueLabel = action.dueLabel)
                    }.onSuccess {
                        _statusBanner.value = "Task added: ${action.title}"
                    }.onFailure { _chatError.value = it.localizedMessage }
                }
                is JarvisActionProposal.SaveNote -> {
                    runCatching {
                        repository.addNote(action.title, action.content, action.tag)
                    }.onSuccess {
                        _statusBanner.value = "Note saved: ${action.title}"
                    }.onFailure { _chatError.value = it.localizedMessage }
                }
                is JarvisActionProposal.RememberPreference -> {
                    if (!preferences.value.memoryEnabled) {
                        _chatError.value = "Memory storage is disabled in System settings."
                        return@launch
                    }
                    runCatching {
                        repository.saveMemory(action.key, action.value)
                    }.onSuccess {
                        _statusBanner.value = "Preference remembered: ${action.key}"
                    }.onFailure { _chatError.value = it.localizedMessage }
                }
                is JarvisActionProposal.OpenUrl -> {
                    toolsRepository.openUrl(context, action.url)
                        .onFailure { _chatError.value = "Could not open URL: ${it.localizedMessage}" }
                }
                is JarvisActionProposal.DraftEmail -> {
                    toolsRepository.openEmailDraft(context, action.recipient, action.subject, action.body)
                        .onFailure { _chatError.value = "No email client available on device." }
                }
                is JarvisActionProposal.SetAlarm -> {
                    toolsRepository.openSetAlarm(context, action.hour, action.minute, action.label)
                        .onFailure { _chatError.value = "Clock alarm intent unavailable on device." }
                }
                is JarvisActionProposal.CreateCalendarEvent -> {
                    toolsRepository.openCalendarEventDraft(context, action.title, action.description)
                        .onFailure { _chatError.value = "No calendar app available on device." }
                }
            }
        }
    }

    private suspend fun executeActionWithoutContext(action: JarvisActionProposal) {
        when (action) {
            is JarvisActionProposal.CreateTask -> {
                runCatching { repository.addTask(action.title, priority = action.priority, dueLabel = action.dueLabel) }
                _statusBanner.value = "Task added: ${action.title}"
            }
            is JarvisActionProposal.SaveNote -> {
                runCatching { repository.addNote(action.title, action.content, action.tag) }
                _statusBanner.value = "Note saved: ${action.title}"
            }
            is JarvisActionProposal.RememberPreference -> {
                if (preferences.value.memoryEnabled) {
                    runCatching { repository.saveMemory(action.key, action.value) }
                    _statusBanner.value = "Remembered: ${action.key}"
                }
            }
            else -> {
                // External app intents always require explicit tap confirmation for security
                _pendingActions.value = _pendingActions.value + action
            }
        }
    }

    fun dismissAction(actionId: String) {
        _pendingActions.value = _pendingActions.value.filterNot { it.id == actionId }
    }

    fun refreshWeather(city: String) {
        viewModelScope.launch {
            _weatherLoading.value = true
            _weatherError.value = null
            toolsRepository.fetchWeatherByCity(city)
                .onSuccess { _weatherReport.value = it }
                .onFailure { _weatherError.value = it.localizedMessage ?: "Weather lookup failed." }
            _weatherLoading.value = false
        }
    }

    fun refreshWeatherByCoords(lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherLoading.value = true
            _weatherError.value = null
            toolsRepository.fetchWeatherByCoordinates(lat, lon)
                .onSuccess { _weatherReport.value = it }
                .onFailure { _weatherError.value = it.localizedMessage ?: "Weather lookup failed." }
            _weatherLoading.value = false
        }
    }

    fun runWebSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _webSearchLoading.value = true
            toolsRepository.searchWeb(query)
                .onSuccess { _lastSearchHits.value = it }
                .onFailure { _chatError.value = it.localizedMessage ?: "Web search failed." }
            _webSearchLoading.value = false
        }
    }

    fun refreshNews(topic: String) {
        viewModelScope.launch {
            _newsLoading.value = true
            toolsRepository.fetchLatestNews(topic)
                .onSuccess { _newsArticles.value = it }
                .onFailure { _statusBanner.value = it.localizedMessage }
            _newsLoading.value = false
        }
    }

    fun evaluateMath(expr: String): String {
        return toolsRepository.evaluateMath(expr).fold(
            onSuccess = { result ->
                if (result % 1.0 == 0.0 && result in -1e12..1e12) {
                    result.toLong().toString()
                } else {
                    String.format(Locale.US, "%.6f", result).trimEnd('0').trimEnd('.')
                }
            },
            onFailure = { err -> "Error: ${err.message}" },
        )
    }

    fun addTask(title: String, notes: String, priority: String, dueLabel: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.addTask(title, notes, priority, dueLabel) }
                .onSuccess { _statusBanner.value = "Task added." }
                .onFailure { _chatError.value = it.localizedMessage }
        }
    }

    fun toggleTask(task: JarvisTask) {
        viewModelScope.launch {
            runCatching { repository.toggleTaskCompleted(task) }
                .onFailure { _chatError.value = it.localizedMessage }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteTask(taskId) }
                .onSuccess { _statusBanner.value = "Task removed." }
                .onFailure { _chatError.value = it.localizedMessage }
        }
    }

    fun addNote(title: String, content: String, tag: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.addNote(title, content, tag) }
                .onSuccess { _statusBanner.value = "Note saved." }
                .onFailure { _chatError.value = it.localizedMessage }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteNote(noteId) }
                .onSuccess { _statusBanner.value = "Note deleted." }
                .onFailure { _chatError.value = it.localizedMessage }
        }
    }

    fun addMemory(key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        if (!preferences.value.memoryEnabled) {
            _statusBanner.value = "Enable Memory Permission in System first."
            return
        }
        viewModelScope.launch {
            runCatching { repository.saveMemory(key, value) }
                .onSuccess { _statusBanner.value = "Memory saved." }
                .onFailure { _chatError.value = it.localizedMessage }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteMemory(memoryId) }
                .onSuccess { _statusBanner.value = "Memory erased." }
                .onFailure { _chatError.value = it.localizedMessage }
        }
    }

    fun updatePreferences(updated: UserPreferences) {
        viewModelScope.launch {
            runCatching { repository.updatePreferences(updated) }
                .onSuccess { _statusBanner.value = "System configuration updated." }
                .onFailure { _chatError.value = it.localizedMessage }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { repository.clearConversationHistory() }
                .onSuccess { _statusBanner.value = "Conversation history cleared." }
                .onFailure { _chatError.value = it.localizedMessage }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "JarvisViewModel"
    }
}
