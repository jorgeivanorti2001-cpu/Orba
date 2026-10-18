package com.orba.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.orba.app.data.Note
import com.orba.app.ui.formatTime
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.appBackground
import com.orba.app.ui.theme.blockTouches
import com.orba.app.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Pantalla completa de notas. El cuadro de escritura queda siempre justo encima del
 * teclado, así se ve lo que se escribe mientras se escribe.
 */
@Composable
fun NotesScreen(
    title: String,
    notes: List<Note>,
    autoFocus: Boolean,
    currentPositionMs: () -> Long?,
    onSeek: (Long) -> Unit,
    onAdd: (String, Long?) -> Unit,
    onUpdate: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val listState = rememberLazyListState()
    var lastCount by remember { mutableStateOf(notes.size) }
    LaunchedEffect(notes.size) {
        if (notes.size > lastCount && notes.isNotEmpty()) listState.animateScrollToItem(notes.lastIndex)
        lastCount = notes.size
    }

    Box(Modifier.fillMaxSize().appBackground().blockTouches()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
                Column(Modifier.weight(1f)) {
                    Text("Notas", style = MaterialTheme.typography.titleLarge)
                    Text(
                        title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${notes.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Coral,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }

            var editing by remember { mutableStateOf<Note?>(null) }

            if (notes.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Todavía no hay notas.\nCon el reloj activo, cada nota guarda el minuto exacto del video y luego te lleva ahí.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(notes, key = { it.id }) { n ->
                        NoteCard(
                            note = n,
                            highlighted = editing?.id == n.id,
                            onSeek = onSeek,
                            onEdit = { editing = n },
                            onDelete = { onDelete(n.id); if (editing?.id == n.id) editing = null },
                        )
                    }
                }
            }

            NoteComposer(
                editing = editing,
                autoFocus = autoFocus,
                currentPositionMs = currentPositionMs,
                onSave = { text, pos ->
                    val e = editing
                    if (e != null) onUpdate(e.id, text) else onAdd(text, pos)
                    editing = null
                },
                onCancelEdit = { editing = null },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
    }
}

@Composable
private fun NoteComposer(
    editing: Note?,
    autoFocus: Boolean,
    currentPositionMs: () -> Long?,
    onSave: (String, Long?) -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var stamp by rememberSaveable { mutableStateOf(true) }
    var position by remember { mutableStateOf<Long?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        while (isActive) {
            position = currentPositionMs()
            delay(500)
        }
    }
    LaunchedEffect(editing?.id) {
        if (editing != null) {
            text = editing.text
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(200)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    Column(modifier.glass(RoundedCornerShape(22.dp), strength = 1.4f).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (editing != null) {
                Text("Editando nota", style = MaterialTheme.typography.labelLarge, color = Coral, modifier = Modifier.weight(1f).padding(start = 6.dp))
                IconButton(onClick = { text = ""; onCancelEdit() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, "Cancelar edición", Modifier.size(18.dp))
                }
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (stamp) Coral.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                        .clickable { stamp = !stamp }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (stamp) Icons.Rounded.Timer else Icons.Rounded.TimerOff, null,
                        tint = if (stamp) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            !stamp -> "Nota general"
                            position != null -> "En el minuto ${formatTime(position!!)}"
                            else -> "Con minuto del video"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (stamp) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .focusRequester(focus),
                placeholder = { Text("Escribe tu nota…") },
                minLines = 2,
                maxLines = 7,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSave(text, if (stamp) position ?: currentPositionMs() else null)
                        text = ""
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank()) Coral else Color.White.copy(alpha = 0.08f)),
            ) {
                Icon(
                    if (editing != null) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send,
                    "Guardar nota",
                    tint = if (text.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, highlighted: Boolean, onSeek: (Long) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), strength = if (highlighted) 2f else 1f, tint = if (highlighted) Coral else Color.White)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeTag(note.positionMs, onSeek)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Editar", Modifier.size(20.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Borrar", Modifier.size(20.dp)) }
        }
        Text(note.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 10.dp))
    }
}

@Composable
private fun TimeTag(positionMs: Long?, onSeek: (Long) -> Unit) {
    if (positionMs != null) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Coral.copy(alpha = 0.18f))
                .clickable { onSeek(positionMs) }
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(14.dp), tint = Coral)
            Text(formatTime(positionMs), style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    } else {
        Text("Nota general", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Vista corta de una nota, para mostrar debajo de los videos horizontales. */
@Composable
fun NotePreview(note: Note, onSeek: (Long) -> Unit, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
    ) {
        TimeTag(note.positionMs, onSeek)
        Spacer(Modifier.height(6.dp))
        Text(note.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
