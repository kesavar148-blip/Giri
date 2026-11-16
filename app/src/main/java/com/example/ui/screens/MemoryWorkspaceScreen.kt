package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.JarvisMemory
import com.example.data.model.JarvisNote
import com.example.data.model.JarvisTask
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.HudBorderCyan
import com.example.ui.theme.HudPanelElevated
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NominalGreen
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SteelMuted
import com.example.ui.viewmodel.UiDataState

@Composable
fun MemoryWorkspaceScreen(
    tasksState: UiDataState<List<JarvisTask>>,
    notesState: UiDataState<List<JarvisNote>>,
    memoriesState: UiDataState<List<JarvisMemory>>,
    memoryEnabled: Boolean,
    onAddTask: (String, String, String, String) -> Unit,
    onToggleTask: (JarvisTask) -> Unit,
    onDeleteTask: (String) -> Unit,
    onAddNote: (String, String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onAddMemory: (String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onSummarizeNoteWithJarvis: (JarvisNote) -> Unit,
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf("Tasks", "Notes", "Memory")
                tabs.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = selectedSection == idx,
                        onClick = { selectedSection = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                        label = { Text(label) },
                    )
                }
            }

            when (selectedSection) {
                0 -> TasksListSection(
                    tasksState = tasksState,
                    onToggleTask = onToggleTask,
                    onDeleteTask = onDeleteTask,
                )
                1 -> NotesListSection(
                    notesState = notesState,
                    onDeleteNote = onDeleteNote,
                    onSummarizeNote = onSummarizeNoteWithJarvis,
                )
                2 -> MemoriesListSection(
                    memoriesState = memoriesState,
                    memoryEnabled = memoryEnabled,
                    onDeleteMemory = onDeleteMemory,
                )
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = ArcCyan,
            contentColor = ObsidianVoid,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_workspace_item_fab"),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add new item")
        }
    }

    if (showCreateDialog) {
        when (selectedSection) {
            0 -> AddTaskDialog(
                onDismiss = { showCreateDialog = false },
                onConfirm = { title, notes, priority, due ->
                    onAddTask(title, notes, priority, due)
                    showCreateDialog = false
                },
            )
            1 -> AddNoteDialog(
                onDismiss = { showCreateDialog = false },
                onConfirm = { title, content, tag ->
                    onAddNote(title, content, tag)
                    showCreateDialog = false
                },
            )
            2 -> AddMemoryDialog(
                memoryEnabled = memoryEnabled,
                onDismiss = { showCreateDialog = false },
                onConfirm = { key, value ->
                    onAddMemory(key, value)
                    showCreateDialog = false
                },
            )
        }
    }
}

@Composable
private fun TasksListSection(
    tasksState: UiDataState<List<JarvisTask>>,
    onToggleTask: (JarvisTask) -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    when (tasksState) {
        is UiDataState.Loading -> LoadingBox()
        is UiDataState.Error -> ErrorBox(tasksState.message)
        is UiDataState.Success -> {
            val tasks = tasksState.data
            if (tasks.isEmpty()) {
                EmptyBox(
                    icon = Icons.Default.TaskAlt,
                    title = "No Active Tasks",
                    subtitle = "Tap + to create a to-do item, or ask JARVIS to add a reminder.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DeepHudSurface),
                            border = BorderStroke(1.dp, if (task.completed) HudBorderCyan.copy(alpha = 0.4f) else HudBorderCyan),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = { onToggleTask(task) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Toggle task completion",
                                        tint = if (task.completed) NominalGreen else ArcCyan,
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (task.completed) SteelMuted else IceWhite,
                                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                                    )
                                    if (task.notes.isNotBlank()) {
                                        Text(
                                            text = task.notes,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SteelMuted,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val prioColor = when (task.priority) {
                                            "high" -> CrimsonAlert
                                            "low" -> NominalGreen
                                            else -> AmberAlert
                                        }
                                        Text(
                                            text = task.priority.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = prioColor,
                                        )
                                        if (task.dueLabel.isNotBlank()) {
                                            Text(
                                                text = "// ${task.dueLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ArcCyan,
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteTask(task.id) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete task", tint = SteelMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesListSection(
    notesState: UiDataState<List<JarvisNote>>,
    onDeleteNote: (String) -> Unit,
    onSummarizeNote: (JarvisNote) -> Unit,
) {
    when (notesState) {
        is UiDataState.Loading -> LoadingBox()
        is UiDataState.Error -> ErrorBox(notesState.message)
        is UiDataState.Success -> {
            val notes = notesState.data
            if (notes.isEmpty()) {
                EmptyBox(
                    icon = Icons.AutoMirrored.Filled.Note,
                    title = "No Personal Notes",
                    subtitle = "Tap + to save a note or ask JARVIS to save research notes.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DeepHudSurface),
                            border = BorderStroke(1.dp, HudBorderCyan),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(note.title, style = MaterialTheme.typography.titleMedium, color = IceWhite)
                                        if (note.tag.isNotBlank()) {
                                            Text("#${note.tag}", style = MaterialTheme.typography.labelSmall, color = ArcCyan)
                                        }
                                    }
                                    IconButton(onClick = { onSummarizeNote(note) }) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "Summarize note with JARVIS", tint = ArcCyan)
                                    }
                                    IconButton(onClick = { onDeleteNote(note.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete note", tint = SteelMuted)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(note.content, style = MaterialTheme.typography.bodyMedium, color = SteelMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoriesListSection(
    memoriesState: UiDataState<List<JarvisMemory>>,
    memoryEnabled: Boolean,
    onDeleteMemory: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = HudPanelElevated,
            border = BorderStroke(1.dp, if (memoryEnabled) NominalGreen else AmberAlert),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (memoryEnabled) {
                    "Memory Permission: GRANTED — JARVIS remembers these approved preferences."
                } else {
                    "Memory Permission: PAUSED — Enable Memory in System tab to store new preferences."
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (memoryEnabled) NominalGreen else AmberAlert,
                modifier = Modifier.padding(12.dp),
            )
        }

        when (memoriesState) {
            is UiDataState.Loading -> LoadingBox()
            is UiDataState.Error -> ErrorBox(memoriesState.message)
            is UiDataState.Success -> {
                val memories = memoriesState.data
                if (memories.isEmpty()) {
                    EmptyBox(
                        icon = Icons.Default.Psychology,
                        title = "No Stored Memories",
                        subtitle = "Tell JARVIS your preferences (e.g. 'Remember I prefer concise bullet points') or tap +.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(memories, key = { it.id }) { mem ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DeepHudSurface),
                                border = BorderStroke(1.dp, HudBorderCyan),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mem.key, style = MaterialTheme.typography.labelLarge, color = ArcCyan)
                                        Text(mem.value, style = MaterialTheme.typography.bodyMedium, color = IceWhite)
                                    }
                                    IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Forget memory", tint = SteelMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
    var due by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepHudSurface,
        title = { Text("New Task / Reminder", color = IceWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = dialogFieldColors(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    colors = dialogFieldColors(),
                )
                OutlinedTextField(
                    value = due,
                    onValueChange = { due = it },
                    label = { Text("Due / Reminder time (optional)") },
                    singleLine = true,
                    colors = dialogFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("low", "medium", "high").forEach { level ->
                        FilterChip(
                            selected = priority == level,
                            onClick = { priority = level },
                            label = { Text(level.uppercase()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, notes, priority, due) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianVoid),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SteelMuted) }
        },
    )
}

@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("general") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepHudSurface,
        title = { Text("New Personal Note", color = IceWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = dialogFieldColors(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    minLines = 3,
                    colors = dialogFieldColors(),
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag") },
                    singleLine = true,
                    colors = dialogFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, content, tag) },
                enabled = title.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianVoid),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SteelMuted) }
        },
    )
}

@Composable
private fun AddMemoryDialog(
    memoryEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepHudSurface,
        title = { Text("Add User Preference", color = IceWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!memoryEnabled) {
                    Text(
                        "Memory permission is currently disabled in System settings.",
                        color = AmberAlert,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Preference Key (e.g. coding_language)") },
                    singleLine = true,
                    colors = dialogFieldColors(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Preference Value (e.g. Kotlin & Python)") },
                    colors = dialogFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(key, value) },
                enabled = memoryEnabled && key.isNotBlank() && value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianVoid),
            ) {
                Text("Remember")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SteelMuted) }
        },
    )
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ArcCyan)
    }
}

@Composable
private fun ErrorBox(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = CrimsonAlert, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = IceWhite)
        Spacer(modifier = Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SteelMuted)
    }
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ArcCyan,
    unfocusedBorderColor = HudBorderCyan,
    focusedTextColor = IceWhite,
    unfocusedTextColor = IceWhite,
    focusedLabelColor = ArcCyan,
    unfocusedLabelColor = SteelMuted,
)
