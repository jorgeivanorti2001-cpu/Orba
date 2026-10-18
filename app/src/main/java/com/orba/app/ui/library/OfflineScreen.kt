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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.orba.app.data.Category
import com.orba.app.data.DownloadState
import com.orba.app.data.Kind
import com.orba.app.data.Video
import com.orba.app.data.thumbModel
import com.orba.app.ui.CategoryFilter
import com.orba.app.ui.components.CategoryChips
import com.orba.app.ui.components.Pill
import com.orba.app.ui.formatDuration
import com.orba.app.ui.formatSize
import com.orba.app.ui.listen.SongRow
import com.orba.app.ui.listen.startAudio
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.BrandGradient
import com.orba.app.ui.theme.Rose
import com.orba.app.ui.theme.appBackground
import com.orba.app.ui.theme.blockTouches
import com.orba.app.ui.theme.glass

/**
 * "Guardados" desde el Inicio: solo lo que está descargado, para ver y escuchar sin conexión.
 * Por categoría (o sin categoría), en orden o en aleatorio.
 */
@Composable
fun OfflineScreen(
    items: List<Video>,
    videoCategories: List<Category>,
    audioCategories: List<Category>,
    showVideo: Boolean,
    showMusic: Boolean,
    player: Player?,
    currentId: Long?,
    onPlayVideos: (CategoryFilter, Boolean, Long?) -> Unit,
    onAudioStarted: () -> Unit,
    onFavorite: (Long, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var kind by remember { mutableIntStateOf(if (showVideo) Kind.VIDEO else Kind.AUDIO) }
    var filter by remember { mutableStateOf<CategoryFilter>(CategoryFilter.All) }

    val saved = items.filter { it.downloadState == DownloadState.DONE && it.kind == kind }
    val shown = when (val f = filter) {
        CategoryFilter.All -> saved
        CategoryFilter.None -> saved.filter { it.categoryId == null }
        CategoryFilter.Favorites -> saved.filter { it.favorite }
        is CategoryFilter.Only -> saved.filter { it.categoryId == f.id }
    }
    val categories = if (kind == Kind.AUDIO) audioCategories else videoCategories

    Box(Modifier.fillMaxSize().appBackground().blockTouches()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
                Column(Modifier.weight(1f)) {
                    Text("Guardados", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Sin conexión · ${saved.size} ${if (kind == Kind.AUDIO) "canciones" else "videos"} · ${formatSize(saved.sumOf { it.fileSizeBytes })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.CloudDone, null, tint = Coral, modifier = Modifier.padding(end = 16.dp))
            }

            if (showVideo && showMusic) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(18.dp))
                        .padding(4.dp),
                ) {
                    KindTab("Videos", Icons.Rounded.SmartDisplay, kind == Kind.VIDEO, Modifier.weight(1f)) {
                        kind = Kind.VIDEO; filter = CategoryFilter.All
                    }
                    KindTab("Audios", Icons.Rounded.MusicNote, kind == Kind.AUDIO, Modifier.weight(1f)) {
                        kind = Kind.AUDIO; filter = CategoryFilter.All
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            CategoryChips(categories, filter, { filter = it }, showFavorites = kind == Kind.AUDIO)
            Spacer(Modifier.height(12.dp))

            if (shown.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Reproducir", Icons.Rounded.PlayArrow, filled = true, Modifier.weight(1f)) {
                        if (kind == Kind.VIDEO) onPlayVideos(filter, false, null)
                        else { startAudio(player, shown, shown.first().id, shuffle = false); onAudioStarted() }
                    }
                    BigButton("Aleatorio", Icons.Rounded.Shuffle, filled = false, Modifier.weight(1f)) {
                        if (kind == Kind.VIDEO) onPlayVideos(filter, true, null)
                        else { startAudio(player, shown, shown.random().id, shuffle = true); onAudioStarted() }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (saved.isEmpty()) "Todavía no hay nada descargado aquí. Lo que descargues se podrá ver y escuchar sin internet."
                        else "No hay descargas en esta categoría.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (kind == Kind.AUDIO) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown, key = { it.id }) { song ->
                        SongRow(
                            song, song.id == currentId,
                            onClick = { startAudio(player, shown, song.id, shuffle = false); onAudioStarted() },
                            onMore = {},
                            onFavorite = { onFavorite(song.id, it) },
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { v ->
                        Column(Modifier.glass(RoundedCornerShape(16.dp)).clickable { onPlayVideos(filter, false, v.id) }.padding(6.dp)) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.04f)),
                            ) {
                                AsyncImage(v.thumbModel(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                formatDuration(v.durationSec).takeIf { it.isNotEmpty() }?.let {
                                    Pill(it, Modifier.align(Alignment.BottomEnd).padding(6.dp))
                                }
                            }
                            Text(
                                v.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindTab(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Coral else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) Color.White else Color.White
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun BigButton(label: String, icon: ImageVector, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(46.dp)
            .then(
                if (filled) Modifier.clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(BrandGradient))
                else Modifier.glass(RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (filled) Color.White else Coral)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (filled) Color.White else Color.White)
    }
}
