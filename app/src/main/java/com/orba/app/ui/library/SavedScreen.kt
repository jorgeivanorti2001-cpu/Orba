package com.orba.app.ui.library

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.orba.app.data.DownloadState
import com.orba.app.data.Kind
import com.orba.app.data.Video
import com.orba.app.data.thumbModel
import com.orba.app.data.isLocal
import com.orba.app.ui.components.GlassChip
import com.orba.app.ui.components.CategoryChips
import com.orba.app.ui.CategoryFilter
import com.orba.app.data.Category
import com.orba.app.ui.formatSize
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.BrandGradient
import com.orba.app.ui.theme.Mint
import com.orba.app.ui.theme.Rose
import com.orba.app.ui.theme.appBackground
import com.orba.app.ui.theme.blockTouches
import com.orba.app.ui.theme.glass

/**
 * Lo que está descargado en el teléfono. Se pueden elegir varios (o todos) y quitar
 * sus descargas para liberar espacio: los enlaces siguen en la app.
 */
@Composable
fun SavedScreen(
    items: List<Video>,
    videoCategories: List<Category>,
    audioCategories: List<Category>,
    onDelete: (Set<Long>) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var kindFilter by remember { mutableStateOf<Int?>(null) }
    var catFilter by remember { mutableStateOf<CategoryFilter>(CategoryFilter.All) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirm by remember { mutableStateOf(false) }

    val saved = items.filter { it.downloadState == DownloadState.DONE }
        .filter { kindFilter == null || it.kind == kindFilter }
        .filter { v ->
            when (val f = catFilter) {
                CategoryFilter.All -> true
                CategoryFilter.None -> v.categoryId == null
                CategoryFilter.Favorites -> v.favorite
                is CategoryFilter.Only -> v.categoryId == f.id
            }
        }
        .sortedByDescending { it.fileSizeBytes }
    val visibleIds = saved.map { it.id }.toSet()
    val chosen = selected.intersect(visibleIds)
    val chosenBytes = saved.filter { it.id in chosen }.sumOf { it.fileSizeBytes }
    val totalBytes = saved.sumOf { it.fileSizeBytes }
    val allChosen = saved.isNotEmpty() && chosen.size == saved.size

    Box(Modifier.fillMaxSize().appBackground().blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
                Column(Modifier.weight(1f)) {
                    Text("Guardados", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${saved.size} en el teléfono · ${formatSize(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { selected = if (allChosen) emptySet() else visibleIds }, enabled = saved.isNotEmpty()) {
                    Text(if (allChosen) "Ninguno" else "Todos")
                }
            }

            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("Todo", kindFilter == null, null) { kindFilter = null; catFilter = CategoryFilter.All }
                GlassChip("Videos", kindFilter == Kind.VIDEO, null) { kindFilter = Kind.VIDEO; catFilter = CategoryFilter.All }
                GlassChip("Audios", kindFilter == Kind.AUDIO, null) { kindFilter = Kind.AUDIO; catFilter = CategoryFilter.All }
            }
            // Por categoría (o sin categoría) dentro de Videos o Audios
            kindFilter?.let { k ->
                CategoryChips(
                    if (k == Kind.AUDIO) audioCategories else videoCategories,
                    catFilter,
                    { catFilter = it },
                    Modifier.padding(bottom = 6.dp),
                )
            }

            if (saved.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay nada descargado aquí.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(saved, key = { it.id }) { item ->
                        val isChosen = item.id in chosen
                        SavedRow(item, isChosen) {
                            selected = if (isChosen) selected - item.id else selected + item.id
                        }
                    }
                }
            }

            // Botón final: siempre a la vista
            val enabled = chosen.isNotEmpty()
            Box(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (enabled) Brush.horizontalGradient(BrandGradient)
                        else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
                    )
                    .clickable(enabled = enabled) { confirm = true },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DeleteSweep, null, tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (enabled) "Liberar ${formatSize(chosenBytes)} (${chosen.size})" else "Elige qué quitar del teléfono",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Quitar ${chosen.size} descargas?") },
            text = {
                Text("Se liberan ${formatSize(chosenBytes)}. Los enlaces, categorías y notas se conservan; podrás verlos en línea o volver a descargarlos.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(chosen)
                    selected = emptySet()
                    confirm = false
                }) { Text("Quitar", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SavedRow(item: Video, chosen: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp), strength = if (chosen) 2f else 1f, tint = if (chosen) Coral else Color.White)
            .clickable(onClick = onToggle)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.kind == Kind.AUDIO) Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.3f))
            AsyncImage(model = item.thumbModel(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudDone, null, tint = if (item.isLocal()) Color(0xFFFFC94D) else Mint, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    (if (item.isLocal()) "Del teléfono · se quita de la lista" else if (item.kind == Kind.AUDIO) "Audio" else "Video") +
                        " · " + formatSize(item.fileSizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Checkbox(
            checked = chosen,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Coral, checkmarkColor = Color.White),
        )
    }
}
