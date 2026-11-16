package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.ai.JarvisActionProposal
import com.example.data.model.ChatMessageEntity
import com.example.data.model.UserPreferences
import com.example.ui.components.AssistantVisualState
import com.example.ui.components.CameraScannerDialog
import com.example.ui.components.JarvisCoreVisualizer
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.HudBorderCyan
import com.example.ui.theme.HudPanelElevated
import com.example.ui.theme.HudPanelHighlight
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NominalGreen
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SteelMuted
import com.example.ui.viewmodel.PendingAttachment
import com.example.ui.viewmodel.UiDataState
import com.example.voice.VoicePhase

@Composable
fun HudChatScreen(
    prefs: UserPreferences,
    messagesState: UiDataState<List<ChatMessageEntity>>,
    isThinking: Boolean,
    streamingReply: String,
    chatError: String?,
    pendingAttachment: PendingAttachment?,
    pendingActions: List<JarvisActionProposal>,
    voicePhase: VoicePhase,
    rmsLevel: Float,
    partialTranscript: String,
    wakeWordArmed: Boolean,
    onToggleWakeWord: (Boolean) -> Unit,
    onStartVoiceCommand: () -> Unit,
    onStopVoice: () -> Unit,
    onStopSpeaking: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onSendPrompt: (String, Boolean) -> Unit,
    onAttachImage: (Bitmap, String) -> Unit,
    onAttachDocument: (String, String) -> Unit,
    onClearAttachment: () -> Unit,
    onApproveAction: (JarvisActionProposal) -> Unit,
    onDismissAction: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var webSearchMode by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    val visualState = when {
        isThinking -> AssistantVisualState.THINKING
        voicePhase == VoicePhase.COMMAND_LISTENING -> AssistantVisualState.LISTENING
        voicePhase == VoicePhase.SPEAKING -> AssistantVisualState.SPEAKING
        wakeWordArmed || voicePhase == VoicePhase.WAKE_LISTENING -> AssistantVisualState.WAKE_READY
        else -> AssistantVisualState.IDLE
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionNotice = null
            onStartVoiceCommand()
        } else {
            permissionNotice = "Microphone permission is required for voice commands and wake word detection."
        }
    }

    val wakePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionNotice = null
            onToggleWakeWord(true)
        } else {
            permissionNotice = "Microphone permission is required to listen for '${prefs.wakeWord}'."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionNotice = null
            showCameraScanner = true
        } else {
            permissionNotice = "Camera permission is required for optical visual assistance."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val bmp = decodeScaledBitmap(context, uri)
            if (bmp != null) {
                onAttachImage(bmp, "Image selected")
            } else {
                permissionNotice = "Could not load selected image."
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = readDocumentText(context, uri)
            if (text != null) {
                onAttachDocument("Document attached (${text.length} chars)", text)
            } else {
                permissionNotice = "Please select a text, markdown, CSV, JSON, or code file."
            }
        }
    }

    if (showCameraScanner) {
        CameraScannerDialog(
            onDismiss = { showCameraScanner = false },
            onCaptured = { bitmap, query ->
                showCameraScanner = false
                onAttachImage(bitmap, "Camera capture")
                onSendPrompt(query, false)
            },
        )
    }

    val hasMessages = (messagesState as? UiDataState.Success)?.data?.isNotEmpty() == true || streamingReply.isNotBlank()
    val handleCoreTap: () -> Unit = {
        if (voicePhase == VoicePhase.COMMAND_LISTENING) {
            onStopVoice()
        } else if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
            onStartVoiceCommand()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Subtle atmospheric Stark-HUD radial luminescence over ObsidianVoid
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ArcBlue.copy(alpha = 0.14f),
                            ArcCyan.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.24f),
                        radius = size.maxDimension * 0.65f,
                    ),
                )
            },
    ) {
        val isWide = maxWidth >= 720.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CoreTelemetryPane(
                    prefs = prefs,
                    visualState = visualState,
                    rmsLevel = rmsLevel,
                    partialTranscript = partialTranscript,
                    wakeWordArmed = wakeWordArmed,
                    onCoreClick = handleCoreTap,
                    onToggleWakeWord = { enable ->
                        if (!enable) {
                            onToggleWakeWord(false)
                        } else if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                            onToggleWakeWord(true)
                        } else {
                            wakePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onToggleAutoSpeak = onToggleAutoSpeak,
                    onQuickPrompt = { prompt, useSearch -> onSendPrompt(prompt, useSearch) },
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight(),
                )
                ChatWorkspaceColumn(
                    prefs = prefs,
                    messagesState = messagesState,
                    isThinking = isThinking,
                    streamingReply = streamingReply,
                    chatError = chatError ?: permissionNotice,
                    pendingAttachment = pendingAttachment,
                    pendingActions = pendingActions,
                    voicePhase = voicePhase,
                    inputText = inputText,
                    webSearchMode = webSearchMode,
                    onInputChange = { inputText = it },
                    onToggleWebSearch = { webSearchMode = !webSearchMode },
                    onSend = {
                        val toSend = inputText
                        inputText = ""
                        onSendPrompt(toSend, webSearchMode)
                    },
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onOpenCamera = {
                        if (hasPermission(context, Manifest.permission.CAMERA)) {
                            showCameraScanner = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onPickDocument = {
                        documentPickerLauncher.launch(arrayOf("text/*", "application/json"))
                    },
                    onClearAttachment = onClearAttachment,
                    onMicClick = handleCoreTap,
                    onStopSpeaking = onStopSpeaking,
                    onApproveAction = onApproveAction,
                    onDismissAction = onDismissAction,
                    onDismissError = {
                        permissionNotice = null
                        onDismissError()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactHudHeader(
                    prefs = prefs,
                    visualState = visualState,
                    rmsLevel = rmsLevel,
                    partialTranscript = partialTranscript,
                    wakeWordArmed = wakeWordArmed,
                    hasMessages = hasMessages,
                    onCoreClick = handleCoreTap,
                    onToggleWakeWord = { enable ->
                        if (!enable) {
                            onToggleWakeWord(false)
                        } else if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                            onToggleWakeWord(true)
                        } else {
                            wakePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onToggleAutoSpeak = onToggleAutoSpeak,
                    onQuickPrompt = { prompt, useSearch -> onSendPrompt(prompt, useSearch) },
                )

                ChatWorkspaceColumn(
                    prefs = prefs,
                    messagesState = messagesState,
                    isThinking = isThinking,
                    streamingReply = streamingReply,
                    chatError = chatError ?: permissionNotice,
                    pendingAttachment = pendingAttachment,
                    pendingActions = pendingActions,
                    voicePhase = voicePhase,
                    inputText = inputText,
                    webSearchMode = webSearchMode,
                    onInputChange = { inputText = it },
                    onToggleWebSearch = { webSearchMode = !webSearchMode },
                    onSend = {
                        val toSend = inputText
                        inputText = ""
                        onSendPrompt(toSend, webSearchMode)
                    },
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onOpenCamera = {
                        if (hasPermission(context, Manifest.permission.CAMERA)) {
                            showCameraScanner = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onPickDocument = {
                        documentPickerLauncher.launch(arrayOf("text/*", "application/json"))
                    },
                    onClearAttachment = onClearAttachment,
                    onMicClick = {
                        if (voicePhase == VoicePhase.COMMAND_LISTENING) {
                            onStopVoice()
                        } else if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                            onStartVoiceCommand()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopSpeaking = onStopSpeaking,
                    onApproveAction = onApproveAction,
                    onDismissAction = onDismissAction,
                    onDismissError = {
                        permissionNotice = null
                        onDismissError()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CompactHudHeader(
    prefs: UserPreferences,
    visualState: AssistantVisualState,
    rmsLevel: Float,
    partialTranscript: String,
    wakeWordArmed: Boolean,
    hasMessages: Boolean,
    onCoreClick: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onQuickPrompt: (String, Boolean) -> Unit,
) {
    val coreSize by animateDpAsState(
        targetValue = if (!hasMessages || visualState == AssistantVisualState.LISTENING || visualState == AssistantVisualState.SPEAKING) {
            188.dp
        } else {
            142.dp
        },
        animationSpec = tween(durationMillis = 280),
        label = "compact_core_size",
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = DeepHudSurface.copy(alpha = 0.88f),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, HudBorderCyan.copy(alpha = 0.85f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val bracketLen = 14.dp.toPx()
                        val stroke = 1.8.dp.toPx()
                        val inset = 8.dp.toPx()
                        val bracketColor = ArcCyan.copy(alpha = 0.55f)
                        // Top-left bracket
                        drawLine(bracketColor, Offset(inset, inset), Offset(inset + bracketLen, inset), stroke, StrokeCap.Round)
                        drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + bracketLen), stroke, StrokeCap.Round)
                        // Top-right bracket
                        drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset - bracketLen, inset), stroke, StrokeCap.Round)
                        drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + bracketLen), stroke, StrokeCap.Round)
                        // Bottom-left bracket
                        drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset + bracketLen, size.height - inset), stroke, StrokeCap.Round)
                        drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset, size.height - inset - bracketLen), stroke, StrokeCap.Round)
                        // Bottom-right bracket
                        drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - bracketLen, size.height - inset), stroke, StrokeCap.Round)
                        drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - bracketLen), stroke, StrokeCap.Round)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Top telemetry bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "J.A.R.V.I.S. // HUD",
                            style = MaterialTheme.typography.titleMedium,
                            color = IceWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${prefs.selectedModel} // ${prefs.personality.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ArcCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = wakeWordArmed,
                            onClick = { onToggleWakeWord(!wakeWordArmed) },
                            label = {
                                Text(
                                    text = if (wakeWordArmed) "Wake: ON" else "\"${prefs.wakeWord}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Wake word toggle",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ArcCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ArcCyan,
                                selectedLeadingIconColor = ArcCyan,
                            ),
                            modifier = Modifier.testTag("wake_word_chip"),
                        )

                        IconButton(
                            onClick = onToggleAutoSpeak,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toggle_tts_button"),
                        ) {
                            Icon(
                                imageVector = if (prefs.autoSpeakEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Toggle voice output",
                                tint = if (prefs.autoSpeakEnabled) ArcCyan else SteelMuted,
                            )
                        }
                    }
                }

                // Central circular arc-reactor visualizer
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    JarvisCoreVisualizer(
                        visualState = visualState,
                        rmsLevel = rmsLevel,
                        sizeDp = coreSize,
                        statusSubtitle = when {
                            visualState == AssistantVisualState.LISTENING -> "TAP TO STOP"
                            wakeWordArmed -> "\"${prefs.wakeWord}\""
                            else -> "TAP CORE"
                        },
                        onCoreClick = onCoreClick,
                    )
                }

                if (partialTranscript.isNotBlank()) {
                    Text(
                        text = "\"$partialTranscript\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = ArcCyan,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        QuickChipsRow(onQuickPrompt = onQuickPrompt)
    }
}

@Composable
private fun CoreTelemetryPane(
    prefs: UserPreferences,
    visualState: AssistantVisualState,
    rmsLevel: Float,
    partialTranscript: String,
    wakeWordArmed: Boolean,
    onCoreClick: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onQuickPrompt: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = DeepHudSurface.copy(alpha = 0.90f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, HudBorderCyan),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "J.A.R.V.I.S. HUD",
                    style = MaterialTheme.typography.headlineMedium,
                    color = IceWhite,
                )
                Text(
                    text = "${prefs.selectedModel} // ${prefs.personality.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcCyan,
                )
                Spacer(modifier = Modifier.height(16.dp))
                JarvisCoreVisualizer(
                    visualState = visualState,
                    rmsLevel = rmsLevel,
                    sizeDp = 216.dp,
                    statusSubtitle = if (wakeWordArmed) "\"${prefs.wakeWord}\"" else "TAP CORE",
                    onCoreClick = onCoreClick,
                )
                if (partialTranscript.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$partialTranscript\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArcCyan,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = wakeWordArmed,
                    onClick = { onToggleWakeWord(!wakeWordArmed) },
                    label = {
                        Text(if (wakeWordArmed) "Wake Armed: ${prefs.wakeWord}" else "Arm Wake Word")
                    },
                    leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = prefs.autoSpeakEnabled,
                    onClick = onToggleAutoSpeak,
                    label = {
                        Text(if (prefs.autoSpeakEnabled) "Speech Output: ON" else "Speech Output: Muted")
                    },
                    leadingIcon = {
                        Icon(
                            if (prefs.autoSpeakEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                QuickChipsRow(onQuickPrompt = onQuickPrompt)
            }
        }
    }
}

@Composable
private fun QuickChipsRow(
    onQuickPrompt: (String, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val chips = listOf(
            Triple("Weather", "What is the current weather in my city?", false),
            Triple("Briefing", "Give me today's date, time, and top news briefing.", true),
            Triple("Translate", "Translate 'Good morning, how is everything?' to Spanish, French, and Japanese.", false),
            Triple("Draft Email", "Help me write a concise, polite follow-up email for a project update.", false),
            Triple("Remind Me", "Add a high-priority task to review system diagnostics tonight.", false),
        )
        chips.forEach { (label, prompt, search) ->
            AssistChip(
                onClick = { onQuickPrompt(prompt, search) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = HudPanelElevated,
                    labelColor = IceWhite,
                ),
                border = BorderStroke(1.dp, HudBorderCyan.copy(alpha = 0.7f)),
            )
        }
    }
}

@Composable
private fun ChatWorkspaceColumn(
    prefs: UserPreferences,
    messagesState: UiDataState<List<ChatMessageEntity>>,
    isThinking: Boolean,
    streamingReply: String,
    chatError: String?,
    pendingAttachment: PendingAttachment?,
    pendingActions: List<JarvisActionProposal>,
    voicePhase: VoicePhase,
    inputText: String,
    webSearchMode: Boolean,
    onInputChange: (String) -> Unit,
    onToggleWebSearch: () -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onOpenCamera: () -> Unit,
    onPickDocument: () -> Unit,
    onClearAttachment: () -> Unit,
    onMicClick: () -> Unit,
    onStopSpeaking: () -> Unit,
    onApproveAction: (JarvisActionProposal) -> Unit,
    onDismissAction: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val messages = (messagesState as? UiDataState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size, streamingReply) {
        if (messages.isNotEmpty() || streamingReply.isNotBlank()) {
            val target = (messages.size + if (streamingReply.isNotBlank()) 1 else 0 - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(visible = chatError != null) {
            Surface(
                color = CrimsonAlert.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, CrimsonAlert),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chatError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrimsonAlert,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissError) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = CrimsonAlert)
                    }
                }
            }
        }

        // Conversation feed
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(DeepHudSurface.copy(alpha = 0.7f)),
        ) {
            when (messagesState) {
                is UiDataState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ArcCyan)
                    }
                }
                is UiDataState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = messagesState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonAlert,
                        )
                    }
                }
                is UiDataState.Success -> {
                    if (messages.isEmpty() && streamingReply.isBlank()) {
                        EmptyChatHudPlaceholder(
                            preferredName = prefs.preferredName.ifBlank { prefs.displayName },
                            wakeWord = prefs.wakeWord,
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(messages, key = { it.id.ifBlank { "${it.role}_${it.hashCode()}" } }) { msg ->
                                ChatBubble(message = msg)
                            }
                            if (streamingReply.isNotBlank()) {
                                item(key = "streaming_bubble") {
                                    ChatBubble(
                                        message = ChatMessageEntity(
                                            role = "assistant",
                                            content = streamingReply,
                                            modelUsed = prefs.selectedModel,
                                        ),
                                        isStreaming = true,
                                    )
                                }
                            } else if (isThinking) {
                                item(key = "thinking_indicator") {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = ArcCyan,
                                            strokeWidth = 2.dp,
                                        )
                                        Text(
                                            text = "JARVIS is computing...",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ArcCyan,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action confirmation cards (Security Gate)
        if (pendingActions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pendingActions.forEach { action ->
                    ActionConfirmationCard(
                        action = action,
                        onApprove = { onApproveAction(action) },
                        onDismiss = { onDismissAction(action.id) },
                    )
                }
            }
        }

        // Pending attachment preview
        if (pendingAttachment != null) {
            Surface(
                color = HudPanelElevated,
                border = BorderStroke(1.dp, ArcCyan),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (pendingAttachment.bitmap != null) {
                        Image(
                            bitmap = pendingAttachment.bitmap.asImageBitmap(),
                            contentDescription = "Attached image preview",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = ArcCyan)
                    }
                    Text(
                        text = pendingAttachment.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceWhite,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClearAttachment) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = SteelMuted)
                    }
                }
            }
        }

        // Input & Multimodal Dock
        Surface(
            color = DeepHudSurface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, HudBorderCyan),
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = onPickImage,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("attach_image_button"),
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Attach photo", tint = ArcCyan)
                    }
                    IconButton(
                        onClick = onOpenCamera,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("camera_vision_button"),
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera visual assistance", tint = ArcCyan)
                    }
                    IconButton(
                        onClick = onPickDocument,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("attach_doc_button"),
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach document", tint = ArcCyan)
                    }

                    FilterChip(
                        selected = webSearchMode,
                        onClick = onToggleWebSearch,
                        label = { Text("Web Grounding", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ArcBlue.copy(alpha = 0.25f),
                            selectedLabelColor = ArcCyan,
                            selectedLeadingIconColor = ArcCyan,
                        ),
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (voicePhase == VoicePhase.SPEAKING) {
                        IconButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("stop_speaking_button"),
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop speaking", tint = AmberAlert)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        placeholder = {
                            Text(
                                if (webSearchMode) "Ask JARVIS with live web search..." else "Speak or type command for JARVIS...",
                                color = SteelMuted,
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = HudBorderCyan,
                            focusedTextColor = IceWhite,
                            unfocusedTextColor = IceWhite,
                        ),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                    )

                    Surface(
                        color = if (voicePhase == VoicePhase.COMMAND_LISTENING) NominalGreen else HudPanelHighlight,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, if (voicePhase == VoicePhase.COMMAND_LISTENING) NominalGreen else ArcCyan),
                    ) {
                        IconButton(
                            onClick = onMicClick,
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("mic_button"),
                        ) {
                            Icon(
                                imageVector = if (voicePhase == VoicePhase.COMMAND_LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Voice command microphone",
                                tint = if (voicePhase == VoicePhase.COMMAND_LISTENING) ObsidianVoid else ArcCyan,
                            )
                        }
                    }

                    Surface(
                        color = ArcCyan,
                        shape = CircleShape,
                    ) {
                        IconButton(
                            onClick = onSend,
                            enabled = !isThinking && (inputText.isNotBlank() || pendingAttachment != null),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("send_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send prompt",
                                tint = ObsidianVoid,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatHudPlaceholder(
    preferredName: String,
    wakeWord: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "SYSTEM ONLINE, ${preferredName.uppercase()}",
            style = MaterialTheme.typography.titleLarge,
            color = ArcCyan,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the microphone or arm \"$wakeWord\", attach a photo or document, use Camera Vision, or type any request below.",
            style = MaterialTheme.typography.bodyMedium,
            color = SteelMuted,
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessageEntity,
    isStreaming: Boolean = false,
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            color = if (isUser) HudPanelHighlight else HudPanelElevated,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isUser) ArcBlue.copy(alpha = 0.7f) else HudBorderCyan,
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (isUser) "YOU" else "J.A.R.V.I.S.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) ArcCyan else NominalGreen,
                    )
                    if (!isUser && message.modelUsed.isNotBlank()) {
                        Text(
                            text = "// ${message.modelUsed}${if (isStreaming) " [STREAM]" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteelMuted,
                        )
                    }
                }
                if (message.attachmentLabel.isNotBlank()) {
                    Text(
                        text = "[${message.attachmentLabel}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberAlert,
                    )
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = IceWhite,
                )
            }
        }
    }
}

@Composable
private fun ActionConfirmationCard(
    action: JarvisActionProposal,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudPanelHighlight),
        border = BorderStroke(1.dp, AmberAlert),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Security, contentDescription = "Action confirmation", tint = AmberAlert)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CONFIRM ACTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberAlert,
                )
                Text(
                    text = action.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IceWhite,
                )
            }
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, SteelMuted),
            ) {
                Text("Dismiss", color = SteelMuted)
            }
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianVoid),
                modifier = Modifier.testTag("approve_action_button"),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Approve")
            }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    val maxDim = 1024
    val scale = minOf(maxDim.toFloat() / raw.width, maxDim.toFloat() / raw.height, 1f)
    if (scale < 1f) {
        Bitmap.createScaledBitmap(
            raw,
            (raw.width * scale).toInt().coerceAtLeast(1),
            (raw.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        raw
    }
}.getOrNull()

private fun readDocumentText(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
        reader.readText().take(12000)
    }
}.getOrNull()
