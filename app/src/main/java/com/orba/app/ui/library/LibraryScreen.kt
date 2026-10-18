package com.orba.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.orba.app.data.Category
import com.orba.app.data.DownloadState
import com.orba.app.data.Video
import com.orba.app.data.thumbModel
import com.orba.app.ui.MainViewModel
import com.orba.app.ui.PlayerUiState
import com.orba.app.ui.components.GlassChip
import com.orba.app.ui.listen.MiniPlayerSpace
import com.orba.app.ui.listen.SongRow
import com.orba.app.ui.listen.startAudio
import com.orba.app.ui.theme.Coral
import com.orba.app.data.Kind
import androidx.media3.common.Player
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SmartDisplay
import com.orba.app.ui.components.CategoryChips
import com.orba.app.ui.components.ManageCategoriesDialog
import com.orba.app.ui.components.Pill
import com.orba.app.ui.formatDuration
import com.orba.app.ui.formatSize
import com.orba.app.ui.theme.NavClearance
import com.orba.app.ui.theme.glass
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.orba.app.ui.components.WebSearchSection
import com.orba.app.ui.matchesQuery

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    player: Player?,
    playerState: PlayerUiState?,
    onOpen: (Video) -> Unit,
    onOptions: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosenKind by vm.libraryKind.collectAsStateWithLifecycle()
    val showMusic by vm.showMusic.collectAsStateWithLifecycle()
    // Orba Music: solo audios. Orba sin unificar: solo videos.
    val kind = when {
        vm.musicOnly -> Kind.AUDIO
        !showMusic -> Kind.VIDEO
        else -> chosenKind
    }
    val notesOnly by vm.libraryNotesOnly.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val audios by vm.audios.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val listen by vm.listenList.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val audioCategories by vm.audioCategories.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val audioFilter by vm.audioFilter.collectAsStateWithLifecycle()
    val noteCounts by vm.noteCounts.collectAsStateWithLifecycle()
    val shuffle by vm.prefs.shuffle.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var managing by remember { mutableStateOf(false) }

    val isAudio = kind == Kind.AUDIO
    // Al escribir se busca en todo (sin importar la categoría elegida), por cualquier parte del nombre
    val base = when {
        query.isNotBlank() -> if (isAudio) audios else videos
        isAudio -> listen
        else -> feed
    }
    val shown = remember(base, query, notesOnly, noteCounts, isAudio) {
        base
            .filter { isAudio || !notesOnly || (noteCounts[it.id] ?: 0) > 0 }
            .filter { it.matchesQuery(query) }
    }
    val webSearch: @Composable () -> Unit = {
        WebSearchSection(
            query = query,
            kind = kind,
            existingUrls = remember(videos, audios, kind) { (if (isAudio) audios else videos).map { it.sourceUrl }.toSet() },
            onSearch = { vm.searchWeb(it) },
            onAdd = { vm.addFromSearch(it, kind) },
            modifier = Modifier.padding(top = 10.dp, end = 8.dp),
        )
    }
    val everything = videos + audios
    val offline = everything.count { it.downloadState == DownloadState.DONE }
    val bytes = everything.sumOf { it.fileSizeBytes }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Biblioteca", style = MaterialTheme.typography.headlineMedium)
                Text(
                    when {
                        vm.musicOnly -> "${audios.size} canciones · ${formatSize(bytes)} guardados"
                        !showMusic -> "${videos.size} videos · ${formatSize(bytes)} guardados"
                        else -> "${videos.size} videos · ${audios.size} audios · ${formatSize(bytes)} guardados"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.savedOpen.value = true }) { Icon(Icons.Rounded.CloudDone, "Guardados ($offline)") }
            IconButton(onClick = { managing = true }) { Icon(Icons.Rounded.FolderOpen, "Gestionar categorías") }
        }

        // Las dos secciones principales: Videos y Audios (cuando la app tiene las dos)
        if (vm.showVideo && showMusic) Row(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(18.dp))
                .padding(4.dp),
        ) {
            Segment("Videos", videos.size, !isAudio, Icons.Rounded.SmartDisplay, Modifier.weight(1f)) {
                vm.libraryKind.value = Kind.VIDEO
            }
            Segment("Audios", audios.size, isAudio, Icons.Rounded.MusicNote, Modifier.weight(1f)) {
                vm.libraryKind.value = Kind.AUDIO
                vm.libraryNotesOnly.value = false
            }
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .glass(RoundedCornerShape(18.dp)),
            placeholder = { Text(if (isAudio) "Buscar canción o artista" else "Buscar video o canal") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        if (isAudio) {
            CategoryChips(audioCategories, audioFilter, { vm.audioFilter.value = it }, showFavorites = true)
        } else {
            CategoryChips(categories, filter, { vm.filter.value = it })
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 12.dp)) {
                GlassChip("Solo con notas", notesOnly, null) { vm.libraryNotesOnly.value = !notesOnly }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty() && query.isNotBlank()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Text(
                    "No está en tu lista",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                )
                webSearch()
                Spacer(Modifier.height(NavClearance + MiniPlayerSpace + 48.dp))
            }
        } else if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        query.isNotBlank() -> "Sin resultados"
                        notesOnly && !isAudio -> "Ningún video tiene notas todavía"
                        isAudio -> "Aquí aparecerán tus canciones"
                        else -> "Aquí aparecerán tus videos"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (isAudio) {
            val currentId = playerState?.mediaId?.toLongOrNull()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = NavClearance + MiniPlayerSpace + 48.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(shown, key = { it.id }) { song ->
                    SongRow(
                        song, song.id == currentId,
                        onClick = { startAudio(player, shown, song.id, shuffle) },
                        onMore = { onOptions(song) },
                        onFavorite = { vm.toggleFavorite(song.id, it) },
                    )
                }
                if (query.isNotBlank()) item(key = "web") { webSearch() }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = NavClearance + MiniPlayerSpace + 48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { v ->
                    VideoCard(
                        video = v,
                        category = categories.firstOrNull { it.id == v.categoryId },
                        noteCount = noteCounts[v.id] ?: 0,
                        onClick = { onOpen(v) },
                        onMore = { onOptions(v) },
                    )
                }
                if (query.isNotBlank()) item(key = "web", span = { GridItemSpan(maxLineSpan) }) { webSearch() }
            }
        }
    }

    if (managing) {
        ManageCategoriesDialog(
            categories = if (isAudio) audioCategories else categories,
            onCreate = { vm.createCategory(it, kind = kind) },
            onRename = { id, name -> vm.renameCategory(id, name) },
            onDelete = { vm.deleteCategory(it) },
            onDismiss = { managing = false },
        )
    }
}

@Composable
private fun Segment(label: String, count: Int, selected: Boolean, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Coral else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) Color.White else Color.White
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("$label · $count", style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun VideoCard(video: Video, category: Category?, noteCount: Int, onClick: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.glass(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.04f)),
        ) {
            AsyncImage(
                model = video.thumbModel(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.align(Alignment.TopStart).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (video.isVertical) Pill("Short", icon = Icons.Rounded.StayCurrentPortrait)
                if (video.unavailableOnline) Pill("Fuera de línea", icon = Icons.Rounded.CloudOff, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (noteCount > 0) Pill("$noteCount", icon = Icons.AutoMirrored.Rounded.Notes)
                if (video.downloadState == DownloadState.DONE) Pill("Guardado", icon = Icons.Rounded.CloudDone, color = Color(0xCC1F6B55))
                formatDuration(video.durationSec).takeIf { it.isNotEmpty() }?.let { Pill(it) }
            }
            if (video.downloadState == DownloadState.DOWNLOADING || video.downloadState == DownloadState.QUEUED) {
                LinearProgressIndicator(
                    progress = { video.downloadProgress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp, start = 2.dp)) {
                Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(category?.name, video.uploader).joinToString(" · ").ifEmpty { "Sin categoría" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.MoreVert, "Opciones") }
        }
    }
}
