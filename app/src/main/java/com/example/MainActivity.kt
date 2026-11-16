package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.JarvisAiRepository
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.JarvisRepository
import com.example.data.tools.JarvisToolsRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.CommandDeckScreen
import com.example.ui.screens.HudChatScreen
import com.example.ui.screens.MemoryWorkspaceScreen
import com.example.ui.screens.SystemSettingsScreen
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.HudPanelHighlight
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SteelMuted
import com.example.ui.theme.JarvisTheme
import com.example.ui.viewmodel.JarvisViewModel
import com.example.voice.JarvisVoiceEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val ROUTE_HUD = "hud"
private const val ROUTE_TOOLS = "tools"
private const val ROUTE_MEMORY = "memory"
private const val ROUTE_SYSTEM = "system"

private data class JarvisNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val tag: String,
)

private val jarvisNavItems = listOf(
    JarvisNavItem(ROUTE_HUD, "HUD", Icons.Default.Hub, "nav_hud"),
    JarvisNavItem(ROUTE_TOOLS, "Deck", Icons.Default.Build, "nav_tools"),
    JarvisNavItem(ROUTE_MEMORY, "Memory", Icons.Default.Psychology, "nav_memory"),
    JarvisNavItem(ROUTE_SYSTEM, "System", Icons.Default.Settings, "nav_system"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                AppNavigation()
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<com.google.firebase.auth.FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            JarvisMainApp(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "Commander",
                userEmail = user.email.orEmpty(),
            )
        }
    }
}

@Composable
private fun JarvisMainApp(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: JarvisViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                JarvisViewModel(
                    repository = JarvisRepository(db),
                    aiRepository = JarvisAiRepository(),
                    toolsRepository = JarvisToolsRepository(),
                    currentUserId = currentUserId,
                    initialDisplayName = userDisplayName,
                )
            }
        },
    )

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val pendingActions by viewModel.pendingActions.collectAsStateWithLifecycle()
    val weatherReport by viewModel.weatherReport.collectAsStateWithLifecycle()
    val weatherLoading by viewModel.weatherLoading.collectAsStateWithLifecycle()
    val weatherError by viewModel.weatherError.collectAsStateWithLifecycle()
    val searchHits by viewModel.lastSearchHits.collectAsStateWithLifecycle()
    val webSearchLoading by viewModel.webSearchLoading.collectAsStateWithLifecycle()
    val newsArticles by viewModel.newsArticles.collectAsStateWithLifecycle()
    val newsLoading by viewModel.newsLoading.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var wakeWordArmed by remember { mutableStateOf(false) }

    val voiceEngine = remember(context) {
        var engineRef: JarvisVoiceEngine? = null
        JarvisVoiceEngine(
            context = context,
            onCommandRecognized = { spokenCommand ->
                viewModel.sendPrompt(
                    rawPrompt = spokenCommand,
                    includeLiveWebSearch = false,
                    onAssistantFinished = { reply ->
                        engineRef?.speak(reply)
                    },
                )
            },
        ).also { engineRef = it }
    }

    val voicePhase by voiceEngine.phase.collectAsStateWithLifecycle()
    val rmsLevel by voiceEngine.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by voiceEngine.partialTranscript.collectAsStateWithLifecycle()
    val voiceStatusMessage by voiceEngine.voiceStatusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(prefs.wakeWord) {
        voiceEngine.updateWakeWord(prefs.wakeWord)
    }

    DisposableEffect(voiceEngine) {
        onDispose {
            voiceEngine.release()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner, voiceStatusMessage) {
        val msg = statusBanner ?: voiceStatusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
            voiceEngine.clearVoiceStatus()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HUD

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid),
    ) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianVoid,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = DeepHudSurface,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        jarvisNavItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(ROUTE_HUD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ArcCyan,
                                    selectedTextColor = ArcCyan,
                                    indicatorColor = HudPanelHighlight,
                                    unselectedIconColor = SteelMuted,
                                    unselectedTextColor = SteelMuted,
                                ),
                                modifier = Modifier.testTag(item.tag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(containerColor = DeepHudSurface) {
                        jarvisNavItems.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(ROUTE_HUD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ArcCyan,
                                    selectedTextColor = ArcCyan,
                                    indicatorColor = HudPanelHighlight,
                                    unselectedIconColor = SteelMuted,
                                    unselectedTextColor = SteelMuted,
                                ),
                                modifier = Modifier.testTag(item.tag),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HUD,
                    ) {
                        composable(ROUTE_HUD) {
                            HudChatScreen(
                                prefs = prefs,
                                messagesState = messagesState,
                                isThinking = isThinking,
                                streamingReply = streamingReply,
                                chatError = chatError,
                                pendingAttachment = pendingAttachment,
                                pendingActions = pendingActions,
                                voicePhase = voicePhase,
                                rmsLevel = rmsLevel,
                                partialTranscript = partialTranscript,
                                wakeWordArmed = wakeWordArmed,
                                onToggleWakeWord = { enabled ->
                                    wakeWordArmed = enabled
                                    voiceEngine.setWakeWordMonitoring(enabled)
                                },
                                onStartVoiceCommand = { voiceEngine.startSingleCommandListen() },
                                onStopVoice = { voiceEngine.stopListening() },
                                onStopSpeaking = { voiceEngine.stopSpeaking() },
                                onToggleAutoSpeak = {
                                    viewModel.updatePreferences(prefs.copy(autoSpeakEnabled = !prefs.autoSpeakEnabled))
                                },
                                onSendPrompt = { prompt, useWeb ->
                                    viewModel.sendPrompt(
                                        rawPrompt = prompt,
                                        includeLiveWebSearch = useWeb,
                                        onAssistantFinished = { reply -> voiceEngine.speak(reply) },
                                    )
                                },
                                onAttachImage = { bmp, label -> viewModel.attachImage(bmp, label) },
                                onAttachDocument = { label, text -> viewModel.attachDocument(label, text) },
                                onClearAttachment = { viewModel.clearAttachment() },
                                onApproveAction = { action -> viewModel.approveAction(action, context) },
                                onDismissAction = { id -> viewModel.dismissAction(id) },
                                onDismissError = { viewModel.dismissError() },
                            )
                        }

                        composable(ROUTE_TOOLS) {
                            CommandDeckScreen(
                                defaultCity = prefs.city.ifBlank { "San Francisco" },
                                weatherReport = weatherReport,
                                weatherLoading = weatherLoading,
                                weatherError = weatherError,
                                searchHits = searchHits,
                                webSearchLoading = webSearchLoading,
                                newsArticles = newsArticles,
                                newsLoading = newsLoading,
                                onRefreshWeatherCity = { city -> viewModel.refreshWeather(city) },
                                onRefreshWeatherCoords = { lat, lon -> viewModel.refreshWeatherByCoords(lat, lon) },
                                onRunWebSearch = { query -> viewModel.runWebSearch(query) },
                                onRefreshNews = { topic -> viewModel.refreshNews(topic) },
                                onEvaluateMath = { expr -> viewModel.evaluateMath(expr) },
                                onSendToAssistant = { prompt, useWeb ->
                                    navController.navigate(ROUTE_HUD) {
                                        popUpTo(ROUTE_HUD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                    viewModel.sendPrompt(
                                        rawPrompt = prompt,
                                        includeLiveWebSearch = useWeb,
                                        onAssistantFinished = { reply -> voiceEngine.speak(reply) },
                                    )
                                },
                            )
                        }

                        composable(ROUTE_MEMORY) {
                            MemoryWorkspaceScreen(
                                tasksState = tasksState,
                                notesState = notesState,
                                memoriesState = memoriesState,
                                memoryEnabled = prefs.memoryEnabled,
                                onAddTask = { title, notes, prio, due -> viewModel.addTask(title, notes, prio, due) },
                                onToggleTask = { task -> viewModel.toggleTask(task) },
                                onDeleteTask = { id -> viewModel.deleteTask(id) },
                                onAddNote = { title, content, tag -> viewModel.addNote(title, content, tag) },
                                onDeleteNote = { id -> viewModel.deleteNote(id) },
                                onAddMemory = { k, v -> viewModel.addMemory(k, v) },
                                onDeleteMemory = { id -> viewModel.deleteMemory(id) },
                                onSummarizeNoteWithJarvis = { note ->
                                    navController.navigate(ROUTE_HUD) {
                                        popUpTo(ROUTE_HUD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                    viewModel.sendPrompt(
                                        rawPrompt = "Summarize this note concisely and highlight key action items:\n\nTitle: ${note.title}\n${note.content}",
                                        includeLiveWebSearch = false,
                                        onAssistantFinished = { reply -> voiceEngine.speak(reply) },
                                    )
                                },
                            )
                        }

                        composable(ROUTE_SYSTEM) {
                            SystemSettingsScreen(
                                prefs = prefs,
                                userEmail = userEmail,
                                speechAvailable = voiceEngine.isSpeechRecognitionAvailable,
                                onSavePreferences = { updated -> viewModel.updatePreferences(updated) },
                                onClearHistory = { viewModel.clearHistory() },
                                onSignOut = {
                                    wakeWordArmed = false
                                    voiceEngine.release()
                                    signOutUser(credentialManager, scope)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
