package com.orba.app.ui.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orba.app.data.Category
import com.orba.app.data.Video
import com.orba.app.engine.Genres
import com.orba.app.ui.components.TextInputDialog
import com.orba.app.ui.theme.Coral
import kotlinx.coroutines.delay

/**
 * Sugerencia de géneros: analiza las canciones una por una (o en bloque), propone la
 * categoría y tú decides cuáles aplicar. Si ya tienes una categoría parecida, usa esa.
 */
@Composable
fun GenreDialog(
    songs: List<Video>,
    categories: List<Category>,
    onApply: (Map<Long, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // id de canción -> categoría propuesta (null = no se encontró)
    val proposals = remember { mutableStateMapOf<Long, String?>() }
    val checked = remember { mutableStateMapOf<Long, Boolean>() }
    var done by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        songs.forEachIndexed { i, song ->
            if (i > 0) delay(1_200) // el catálogo permite unos 20 pedidos por minuto
            val s = runCatching { Genres.suggest(song, categories) }.getOrNull()
            proposals[song.id] = s?.genre
            checked[song.id] = s?.genre != null
            done = i + 1
        }
    }

    val finished = done >= songs.size
    val chosen = songs.mapNotNull { s -> proposals[s.id]?.takeIf { checked[s.id] == true }?.let { s.id to it } }.toMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, null, tint = Coral) },
        title = { Text(if (songs.size == 1) "Sugerir género" else "Organizar por género") },
        text = {
            Column {
                if (!finished) {
                    Text(
                        "Analizando ${done + 1} de ${songs.size}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(6.dp))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / songs.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Coral,
                        drawStopIndicator = {},
                    )
                } else {
                    Text(
                        "Marca las que quieres organizar. Toca el género para cambiarlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(10.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(songs, key = { it.id }) { song ->
                        val known = song.id in proposals
                        val genre = proposals[song.id]
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (known && genre != null) {
                                Checkbox(
                                    checked = checked[song.id] == true,
                                    onCheckedChange = { checked[song.id] = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Coral, checkmarkColor = Color.White),
                                )
                            } else {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    if (!known) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Coral)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                when {
                                    !known -> Text("…", style = MaterialTheme.typography.bodySmall)
                                    else -> GenreChip(
                                        genre = genre,
                                        categories = categories,
                                        onPick = { proposals[song.id] = it; checked[song.id] = true },
                                        onWrite = { editing = song.id },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(chosen); onDismiss() }, enabled = chosen.isNotEmpty()) {
                Text(if (chosen.isEmpty()) "Aplicar" else "Aplicar (${chosen.size})", color = if (chosen.isEmpty()) Color.Unspecified else Coral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    editing?.let { id ->
        TextInputDialog(
            title = "Género o categoría",
            initial = proposals[id].orEmpty(),
            confirm = "Usar",
            onConfirm = { proposals[id] = it.trim(); checked[id] = true; editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun GenreChip(genre: String?, categories: List<Category>, onPick: (String) -> Unit, onWrite: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(50))
                .background(if (genre != null) Coral.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                .clickable { open = true }
                .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                genre ?: "No encontrado · elegir",
                style = MaterialTheme.typography.labelMedium,
                color = if (genre != null) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onPick(c.name); open = false })
            }
            DropdownMenuItem(text = { Text("Escribir otro…", color = Coral) }, onClick = { open = false; onWrite() })
        }
    }
}
