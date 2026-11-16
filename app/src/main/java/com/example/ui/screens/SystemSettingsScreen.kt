package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.UserPreferences
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.HudBorderCyan
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NominalGreen
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SteelMuted

@Composable
fun SystemSettingsScreen(
    prefs: UserPreferences,
    userEmail: String,
    speechAvailable: Boolean,
    onSavePreferences: (UserPreferences) -> Unit,
    onClearHistory: () -> Unit,
    onSignOut: () -> Unit,
) {
    var preferredName by remember(prefs.preferredName) { mutableStateOf(prefs.preferredName) }
    var city by remember(prefs.city) { mutableStateOf(prefs.city) }
    var wakeWord by remember(prefs.wakeWord) { mutableStateOf(prefs.wakeWord) }
    var customInstructions by remember(prefs.customInstructions) { mutableStateOf(prefs.customInstructions) }
    var showClearDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. AI Model & Free-Tier Transparency
        item {
            SettingsCard(title = "AI ENGINE & FREE-TIER ARCHITECTURE", icon = Icons.Default.Memory) {
                Text(
                    text = "Powered by Firebase AI Logic (zero exposed API keys) + free open APIs (Open-Meteo, Wikipedia, DuckDuckGo, Google News RSS, Android Speech).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteelMuted,
                )
                Spacer(modifier = Modifier.height(10.dp))
                val models = listOf(
                    Triple("gemini-3.8-flash", "Gemini 3.8 Flash", "Default • Free Tier"),
                    Triple("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra Fast • Free Tier"),
                    Triple("gemini-4-argon", "Gemini 4 Argon", "Deep STEM • Requires Blaze Plan"),
                )
                models.forEach { (id, title, note) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = IceWhite)
                            Text(
                                note,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (id == "gemini-4-argon") AmberAlert else NominalGreen,
                            )
                        }
                        FilterChip(
                            selected = prefs.selectedModel == id,
                            onClick = { onSavePreferences(prefs.copy(selectedModel = id)) },
                            label = { Text(if (prefs.selectedModel == id) "Active" else "Select") },
                        )
                    }
                }
            }
        }

        // 2. Personality & Profile
        item {
            SettingsCard(title = "CUSTOM AI PERSONALITY", icon = Icons.Default.Psychology) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("butler" to "Butler", "tactical" to "Tactical").forEach { (id, label) ->
                        FilterChip(
                            selected = prefs.personality == id,
                            onClick = { onSavePreferences(prefs.copy(personality = id)) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("friendly" to "Friendly", "architect" to "Architect").forEach { (id, label) ->
                        FilterChip(
                            selected = prefs.personality == id,
                            onClick = { onSavePreferences(prefs.copy(personality = id)) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = preferredName,
                    onValueChange = { preferredName = it },
                    label = { Text("How JARVIS addresses you") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Default weather city") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customInstructions,
                    onValueChange = { customInstructions = it },
                    label = { Text("Custom instructions") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors(),
                )
            }
        }

        // 3. Voice & Wake Word
        item {
            SettingsCard(title = "VOICE & WAKE WORD", icon = Icons.Default.RecordVoiceOver) {
                Text(
                    text = if (speechAvailable) {
                        "Android SpeechRecognizer & TextToSpeech: ONLINE"
                    } else {
                        "Device speech recognition service unavailable; text mode active."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (speechAvailable) NominalGreen else AmberAlert,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = wakeWord,
                    onValueChange = { wakeWord = it },
                    label = { Text("Customizable wake phrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow(
                    title = "Auto-Speak Responses (TTS)",
                    subtitle = "Speak JARVIS replies aloud automatically.",
                    checked = prefs.autoSpeakEnabled,
                    onCheckedChange = { onSavePreferences(prefs.copy(autoSpeakEnabled = it)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSavePreferences(
                            prefs.copy(
                                preferredName = preferredName,
                                city = city,
                                wakeWord = wakeWord.ifBlank { "Hey Jarvis" },
                                customInstructions = customInstructions,
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianVoid),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_settings_button"),
                ) {
                    Text("Save Profile & Voice Settings")
                }
            }
        }

        // 4. Security & Privacy Controls
        item {
            SettingsCard(title = "SECURITY & PRIVACY CENTER", icon = Icons.Default.Security) {
                ToggleRow(
                    title = "Memory Permission",
                    subtitle = "Allow JARVIS to remember your preferences across sessions.",
                    checked = prefs.memoryEnabled,
                    onCheckedChange = { onSavePreferences(prefs.copy(memoryEnabled = it)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow(
                    title = "Confirm Before Actions",
                    subtitle = "Require explicit approval before saving tasks/notes or opening apps.",
                    checked = prefs.confirmActionsEnabled,
                    onCheckedChange = { onSavePreferences(prefs.copy(confirmActionsEnabled = it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    border = BorderStroke(1.dp, CrimsonAlert),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_history_button"),
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonAlert)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Conversation History", color = CrimsonAlert)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Signed in as: ${userEmail.ifBlank { "Google Account" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SteelMuted,
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    border = BorderStroke(1.dp, HudBorderCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = ArcCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", color = IceWhite)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = DeepHudSurface,
            title = { Text("Clear Conversation History?", color = IceWhite) },
            text = {
                Text(
                    "This permanently deletes all saved JARVIS chat messages from your cloud workspace.",
                    color = SteelMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert, contentColor = ObsidianVoid),
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = SteelMuted)
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DeepHudSurface),
        border = BorderStroke(1.dp, HudBorderCyan),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = ArcCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = ArcCyan)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = IceWhite)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SteelMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianVoid,
                checkedTrackColor = ArcCyan,
            ),
        )
    }
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ArcCyan,
    unfocusedBorderColor = HudBorderCyan,
    focusedTextColor = IceWhite,
    unfocusedTextColor = IceWhite,
    focusedLabelColor = ArcCyan,
    unfocusedLabelColor = SteelMuted,
)
