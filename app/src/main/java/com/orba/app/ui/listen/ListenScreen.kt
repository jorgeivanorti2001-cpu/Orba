package com.orba.app.ui.listen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import com.orba.app.ui.searchable
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.orba.app.data.isLocal
import com.orba.app.ui.components.CategoryPickerDialog
import com.orba.app.ui.components.BoostPicker
import com.orba.app.ui.components.boostLabel
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import com.orba.app.ui.components.WebSearchSection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.orba.app.engine.LyricLine
import com.orba.app.engine.Lyrics
import com.orba.app.engine.LyricsResult
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.key
import com.orba.app.data.DownloadState
import com.orba.app.data.Kind
import com.orba.app.data.Prefs
import com.orba.app.data.Video
import com.orba.app.data.thumbModel
import com.orba.app.engine.Engine
import com.orba.app.playback.AudioQueue
import com.orba.app.playback.PlaybackControl
import com.orba.app.ui.MainViewModel
import com.orba.app.ui.PlayerUiState
import com.orba.app.ui.components.CategoryChips
import com.orba.app.ui.components.ManageCategoriesDialog
import com.orba.app.ui.feed.SeekBar
import com.orba.app.ui.formatDuration
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.BrandGradient
import com.orba.app.ui.theme.Ink
import com.orba.app.ui.theme.Mint
import com.orba.app.ui.theme.NavClearance
import com.orba.app.ui.theme.Rose
import com.orba.app.ui.theme.appBackground
import com.orba.app.ui.theme.blockTouches
import com.orba.app.ui.theme.darkGlass
import com.orba.app.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Alto aproximado del mini reproductor, para que las listas dejen espacio. */
val MiniPlayerSpace = 76.dp

/** Arranca la música desde una canción, con la lista visible como cola. */
fun startAudio(player: Player?, list: List<Video>, startId: Long, shuffle: Boolean) {
    val p = player ?: return
    AudioQueue.set(list.map { it.id }, startId, shuffle)
    p.setMediaItem(MediaItem.Builder().setMediaId(startId.toString()).build(), 0)
    p.prepare()
    p.play()
}

@Composable
fun ListenScreen(
    vm: MainViewModel,
    player: Player?,
    playerState: PlayerUiState?,
    onOptions: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list by vm.listenList.collectAsStateWithLifecycle()
    val all by vm.audios.collectAsStateWithLifecycle()
    val categories by vm.audioCategories.collectAsStateWithLifecycle()
    val filter by vm.audioFilter.collectAsStateWithLifecycle()
    val shuffle by vm.prefs.shuffle.collectAsStateWithLifecycle()
    val query by vm.listenQuery.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    var managing by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var moveOpen by remember { mutableStateOf(false) }
    var genreFor by remember { mutableStateOf<List<Video>?>(null) }
    var deleteOpen by remember { mutableStateOf(false) }
    val currentId = playerState?.mediaId?.toLongOrNull()
    val focus = remember { FocusRequester() }

    // Audios del teléfono (MP3, M4A, WAV, FLAC, OGG… y el audio de videos MP4)
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importLocal(uris)
    }

    fun closeSearch() { searching = false; vm.listenQuery.value = "" }
    BackHandler(enabled = searching || selecting) {
        if (selecting) { selecting = false; selected = emptySet() } else closeSearch()
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Escuchar", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (all.size == 1) "1 canción" else "${all.size} canciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { if (searching) closeSearch() else searching = true }) {
                Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Buscar")
            }
            IconButton(onClick = { managing = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Playlists") }
            Box {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(BrandGradient))
                        .clickable { addMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, "Agregar canciones", tint = Color.White)
                    // Anillo discreto mientras se agregan cosas en segundo plano
                    if (importing || adding > 0) CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 2.dp, color = Color.White.copy(alpha = 0.85f))
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Pegar enlaces") },
                        leadingIcon = { Icon(Icons.Rounded.Link, null) },
                        onClick = { addMenu = false; vm.openAdd(Kind.AUDIO) },
                    )
                    DropdownMenuItem(
                        text = { Text("Buscar en internet") },
                        leadingIcon = { Icon(Icons.Rounded.TravelExplore, null) },
                        onClick = { addMenu = false; searching = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Audios del teléfono") },
                        leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, null, tint = LocalYellow) },
                        onClick = { addMenu = false; pickFiles.launch(arrayOf("audio/*", "video/*")) },
                    )
                }
            }
        }

        if (searching) {
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            TextField(
                value = query,
                onValueChange = { vm.listenQuery.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
                    .glass(RoundedCornerShape(18.dp))
                    .focusRequester(focus),
                placeholder = { Text("Canción o artista") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.listenQuery.value = "" }) { Icon(Icons.Rounded.Close, "Borrar") }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        } else {
            Spacer(Modifier.height(12.dp))
            CategoryChips(categories, filter, { vm.audioFilter.value = it }, showFavorites = true)
        }
        Spacer(Modifier.height(12.dp))

        val searchingText = searching && query.isNotBlank()
        if (list.isEmpty() && !searching) {
            EmptyListen(hasAny = all.isNotEmpty()) { vm.openAdd(Kind.AUDIO) }
        } else {
            if (!searching && list.isNotEmpty()) {
                if (selecting) {
                    SelectionBar(
                        count = selected.size,
                        allSelected = selected.size == list.size,
                        onAll = { selected = if (selected.size == list.size) emptySet() else list.map { it.id }.toSet() },
                        onMove = { moveOpen = true },
                        onGenres = { genreFor = list.filter { it.id in selected } },
                        onDelete = { deleteOpen = true },
                        onCancel = { selecting = false; selected = emptySet() },
                    )
                } else {
                    // Botones compactos: reproducir, aleatorio y géneros con IA
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactButton("Reproducir", Icons.Rounded.PlayArrow, filled = true, Modifier.weight(1f)) {
                            vm.prefs.setShuffle(false)
                            startAudio(player, list, list.first().id, shuffle = false)
                        }
                        CompactButton("Aleatorio", Icons.Rounded.Shuffle, filled = false, Modifier.weight(1f)) {
                            vm.prefs.setShuffle(true)
                            startAudio(player, list, list.random().id, shuffle = true)
                        }
                        val noCategory = list.filter { it.categoryId == null }
                        CompactButton("Géneros IA", Icons.Rounded.AutoAwesome, filled = false, Modifier.weight(1f)) {
                            genreFor = noCategory.ifEmpty { list }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            val listState = rememberLazyListState()
            val headerCount = if (searchingText) 1 else 0
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = if (!searching && list.size > 12) 18.dp else 6.dp, top = 4.dp, bottom = NavClearance + MiniPlayerSpace + 48.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (searchingText) {
                        item(key = "header") {
                            Text(
                                if (list.isEmpty()) "No está en tu lista" else "En tu lista · ${list.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                            )
                        }
                    }
                    if (!searching || searchingText) items(list, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            current = song.id == currentId,
                            onClick = {
                                if (selecting) selected = if (song.id in selected) selected - song.id else selected + song.id
                                else startAudio(player, list, song.id, shuffle)
                            },
                            onMore = { onOptions(song) },
                            onFavorite = { vm.toggleFavorite(song.id, it) },
                            selected = if (selecting) song.id in selected else null,
                            onLongClick = {
                                if (!searching) { selecting = true; selected = selected + song.id }
                            },
                        )
                    }
                    if (searching) {
                        item(key = "web") {
                            if (query.isBlank()) {
                                Text(
                                    "Escribe parte del nombre o del artista. Si no la tienes, la puedes buscar en internet y agregarla.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                )
                            } else {
                                WebSearchSection(
                                    query = query,
                                    kind = Kind.AUDIO,
                                    existingUrls = remember(all) { all.map { it.sourceUrl }.toSet() },
                                    onSearch = { vm.searchWeb(it) },
                                    onAdd = { vm.addFromSearch(it, Kind.AUDIO) },
                                    modifier = Modifier.padding(top = 10.dp, end = 8.dp),
                                )
                            }
                        }
                    }
                }
                // Índice de letras al costado derecho (como en las apps de música)
                if (!searching && list.size > 12) {
                    AlphabetIndex(
                        list = list,
                        listState = listState,
                        headerCount = headerCount,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 0.dp, bottom = NavClearance + MiniPlayerSpace),
                    )
                }
            }
        }
    }

    if (managing) {
        ManageCategoriesDialog(
            categories = categories,
            onCreate = { vm.createCategory(it, kind = Kind.AUDIO) },
            onRename = { id, name -> vm.renameCategory(id, name) },
            onDelete = { vm.deleteCategory(it) },
            onDismiss = { managing = false },
        )
    }
    if (moveOpen) {
        CategoryPickerDialog(
            categories = categories,
            current = -1L,
            onPick = { vm.moveToCategory(selected, it); moveOpen = false; selecting = false; selected = emptySet() },
            onCreate = { vm.moveToNewCategory(selected, it, Kind.AUDIO); moveOpen = false; selecting = false; selected = emptySet() },
            onDismiss = { moveOpen = false },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text(if (selected.size == 1) "¿Eliminar 1 canción?" else "¿Eliminar ${selected.size} canciones?") },
            text = { Text("Se borran de tu lista junto con sus descargas. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMany(selected)
                    deleteOpen = false
                    selecting = false
                    selected = emptySet()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancelar") } },
        )
    }
    genreFor?.let { songs ->
        if (songs.isEmpty()) { genreFor = null; return@let }
        GenreDialog(
            songs = songs,
            categories = categories,
            onApply = { vm.applyGenres(it); selecting = false; selected = emptySet() },
            onDismiss = { genreFor = null },
        )
    }
}

/** Marca amarilla de "audio del teléfono" (como la verde de "guardado"). */
val LocalYellow = Color(0xFFFFC94D)

@Composable
private fun CompactButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(36.dp)
            .then(
                if (filled) Modifier.clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(BrandGradient))
                else Modifier.glass(RoundedCornerShape(12.dp))
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (filled) Color.White else Coral, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
    }
}

/** Letra con la que se agrupa una canción en el índice (# para números y símbolos). */
private fun indexLetter(v: Video): Char {
    val c = v.title.searchable().trimStart { !it.isLetterOrDigit() }.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

/**
 * Alfabeto al costado derecho: muestra en qué letra vas mientras bajas, y al tocar
 * (o deslizar el dedo) sobre una letra salta a las canciones que empiezan por ella.
 */
@Composable
private fun AlphabetIndex(
    list: List<Video>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    headerCount: Int,
    modifier: Modifier = Modifier,
) {
    val letters = remember { listOf('#') + ('A'..'Z').toList() }
    val firstIndex = remember(list) {
        val map = mutableMapOf<Char, Int>()
        list.forEachIndexed { i, v -> map.putIfAbsent(indexLetter(v), i) }
        map
    }
    val currentLetter by remember(list) {
        derivedStateOf {
            list.getOrNull((listState.firstVisibleItemIndex - headerCount).coerceAtLeast(0))?.let { indexLetter(it) }
        }
    }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf<Char?>(null) }
    var columnHeight by remember { mutableFloatStateOf(1f) }

    fun jumpTo(letter: Char) {
        // Si no hay canciones con esa letra, va a la siguiente que sí tenga
        val target = letters.dropWhile { it != letter }.firstNotNullOfOrNull { firstIndex[it] } ?: return
        scope.launch { listState.scrollToItem(target + headerCount) }
    }
    fun letterAt(y: Float): Char = letters[((y / columnHeight) * letters.size).toInt().coerceIn(0, letters.size - 1)]

    Box(modifier) {
        Column(
            Modifier
                .width(18.dp)
                .onSizeChanged { columnHeight = it.height.toFloat().coerceAtLeast(1f) }
                .clip(RoundedCornerShape(11.dp))
                .background(if (dragging != null) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                .pointerInput(list) {
                    detectVerticalDragGestures(
                        onDragStart = { o -> letterAt(o.y).let { dragging = it; jumpTo(it) } },
                        onDragEnd = { dragging = null },
                        onDragCancel = { dragging = null },
                        onVerticalDrag = { change, _ ->
                            val l = letterAt(change.position.y)
                            if (l != dragging) { dragging = l; jumpTo(l) }
                        },
                    )
                }
                .pointerInput(list) {
                    detectTapGestures(onTap = { o -> jumpTo(letterAt(o.y)) })
                }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                val has = firstIndex.containsKey(l)
                val active = l == (dragging ?: currentLetter)
                Text(
                    l.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold, lineHeight = 13.sp),
                    color = when {
                        active -> Coral
                        has -> Color.White.copy(alpha = 0.75f)
                        else -> Color.White.copy(alpha = 0.22f)
                    },
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 0.5.dp),
                )
            }
        }
        // Letra grande mientras deslizas el dedo
        dragging?.let { l ->
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-40).dp, y = 120.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center,
            ) { Text(l.toString(), style = MaterialTheme.typography.headlineMedium, color = Color.White) }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onAll: () -> Unit,
    onMove: () -> Unit,
    onGenres: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), strength = 1.4f, tint = Coral)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancelar") }
        Text(
            if (count == 1) "1 elegida" else "$count elegidas",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAll) { Text(if (allSelected) "Ninguna" else "Todas") }
        IconButton(onClick = onGenres, enabled = count > 0) { Icon(Icons.Rounded.AutoAwesome, "Sugerir género", tint = if (count > 0) Coral else Color.Gray) }
        IconButton(onClick = onMove, enabled = count > 0) { Icon(Icons.Rounded.DriveFileMove, "Mover a categoría", tint = if (count > 0) Coral else Color.Gray) }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(Icons.Rounded.DeleteOutline, "Eliminar", tint = if (count > 0) MaterialTheme.colorScheme.error else Color.Gray)
        }
    }
}

@Composable
private fun EmptyListen(hasAny: Boolean, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(88.dp).glass(RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = Coral, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(if (hasAny) "No hay canciones aquí todavía" else "Tu música, sin algoritmos", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pega enlaces de YouTube, SoundCloud, Facebook, TikTok y más. Se guarda solo el audio, para escucharlo con la pantalla apagada y sin conexión.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(BrandGradient))
                .clickable(onClick = onAdd)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("Agregar canciones", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

/** Fila de una canción: carátula, título, artista y estado de descarga. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Video,
    current: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onFavorite: ((Boolean) -> Unit)? = null,
    /** null = modo normal; true/false = modo de selección. */
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    selected == true -> Coral.copy(alpha = 0.18f)
                    current -> Coral.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = if (selected != null) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song, Modifier.size(52.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (current) Coral else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(song.uploader ?: Engine.platformLabel(song.platform), formatDuration(song.durationSec).ifEmpty { null })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        when {
            song.isLocal() -> Icon(Icons.Rounded.PhoneAndroid, "Del teléfono", tint = LocalYellow, modifier = Modifier.size(14.dp))
            song.downloadState == DownloadState.DONE -> Icon(Icons.Rounded.CloudDone, "Guardada", tint = Mint, modifier = Modifier.size(14.dp))
            song.downloadState == DownloadState.DOWNLOADING || song.downloadState == DownloadState.QUEUED -> CircularProgressIndicator(
                progress = { song.downloadProgress },
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.5.dp,
            )
            else -> Unit
        }
        if (selected != null) {
            Spacer(Modifier.width(6.dp))
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = Coral, checkmarkColor = Color.White),
            )
            return@Row
        }
        // Íconos pequeños y pegados a la derecha: así se lee más del nombre de la canción
        if (onFavorite != null) {
            SmallIcon(onClick = { onFavorite(!song.favorite) }) {
                Icon(
                    if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (song.favorite) "Quitar de Me encanta" else "Me encanta",
                    tint = if (song.favorite) Coral else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        SmallIcon(onClick = onMore) {
            Icon(Icons.Rounded.MoreVert, "Opciones", tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(17.dp))
        }
    }
}

/** Botón de ícono compacto (30 dp) para las filas de canciones. */
@Composable
private fun SmallIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(width = 28.dp, height = 36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Cover(song: Video, modifier: Modifier, shape: RoundedCornerShape) {
    val context = LocalContext.current
    Box(modifier.clip(shape).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.3f))
        // Una imagen nueva por cada canción: al pasar con las flechas la carátula cambia siempre
        key(song.id, song.localThumbPath, song.thumbnailUrl) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(song.thumbModel()).crossfade(250).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------- Mini reproductor ----------------

@Composable
fun MiniPlayer(song: Video, player: Player, playerState: PlayerUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var progress by remember(song.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(player, song.id) {
        while (isActive) {
            val d = player.duration
            progress = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .darkGlass(RoundedCornerShape(20.dp), alpha = 0.78f)
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(song, Modifier.size(44.dp), RoundedCornerShape(10.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.uploader ?: Engine.platformLabel(song.platform),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { if (player.playWhenReady) player.pause() else player.play() }) {
                Icon(if (playerState.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Reproducir o pausar")
            }
            IconButton(onClick = { PlaybackControl.next() }) { Icon(Icons.Rounded.SkipNext, "Siguiente") }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 6.dp).height(2.dp).clip(RoundedCornerShape(50)),
            color = Coral,
            trackColor = Color.White.copy(alpha = 0.12f),
            drawStopIndicator = {},
        )
    }
}

// ---------------- Pantalla de reproducción ----------------

@Composable
fun NowPlayingScreen(
    song: Video,
    player: Player,
    playerState: PlayerUiState,
    prefs: Prefs,
    onFavorite: (Boolean) -> Unit,
    onBoost: (Int) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var boostOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shuffle by prefs.shuffle.collectAsStateWithLifecycle()
    val repeat by prefs.audioRepeat.collectAsStateWithLifecycle()
    val showLyrics by prefs.showLyrics.collectAsStateWithLifecycle()
    val playing = playerState.playWhenReady && playerState.playbackState != Player.STATE_ENDED

    var lyrics by remember(song.id) { mutableStateOf<LyricsResult?>(null) }
    var loadingLyrics by remember(song.id) { mutableStateOf(true) }
    var showTranslation by remember { mutableStateOf(true) }
    LaunchedEffect(song.id) {
        loadingLyrics = true
        lyrics = runCatching { Lyrics.load(context, song) }.getOrNull()
        loadingLyrics = false
    }

    // Posición actual, para resaltar la línea que suena
    var position by remember(song.id) { mutableLongStateOf(0L) }
    LaunchedEffect(song.id) {
        while (isActive) {
            if (player.currentMediaItem?.mediaId == song.id.toString()) position = player.currentPosition
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize().appBackground().blockTouches()) {
        AnimatedBackdrop(song, playing)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar", Modifier.size(30.dp)) }
                Text(
                    "Escuchando",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(18.dp))

            // La carátula late con los graves y las ondas salen con cada golpe de la canción
            val pulse = rememberAudioPulse(playing)
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                MusicVisualizer(pulse, coverFraction = 0.68f, modifier = Modifier.fillMaxSize(), cornerFraction = 0.113f)
                Cover(
                    song,
                    Modifier.fillMaxWidth(0.68f).aspectRatio(1f).graphicsLayer {
                        val s = 1f + 0.035f * pulse.bass
                        scaleX = s; scaleY = s
                    },
                    RoundedCornerShape(28.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.uploader ?: Engine.platformLabel(song.platform),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                IconButton(onClick = { onFavorite(!song.favorite) }) {
                    Icon(
                        if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (song.favorite) "Quitar de Me encanta" else "Me encanta",
                        tint = if (song.favorite) Coral else Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SeekBar(player, song.id, Modifier.fillMaxWidth(), thin = true, showTimes = true)
            Spacer(Modifier.height(14.dp))
            // Controles principales, todos en una sola línea y centrados con el botón de reproducir
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Replay10, "Atrás 10 segundos", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { PlaybackControl.previous() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(BrandGradient))
                        .clickable {
                            if (player.playbackState == Player.STATE_ENDED) { player.seekTo(0); player.play() }
                            else if (player.playWhenReady) player.pause() else player.play()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Reproducir o pausar",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
                IconButton(onClick = { PlaybackControl.next() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = {
                    val d = player.duration
                    val target = player.currentPosition + 10_000
                    player.seekTo(if (d > 0) target.coerceAtMost(d - 500) else target)
                }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Forward10, "Adelantar 10 segundos", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            // Opciones: aleatorio, letra y repetir (mismo tamaño y alineadas)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { prefs.setShuffle(!shuffle) }) {
                    Icon(Icons.Rounded.Shuffle, "Aleatorio", tint = if (shuffle) Coral else Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = { prefs.setShowLyrics(!showLyrics) }) {
                    Icon(
                        if (showLyrics) Icons.Rounded.Subtitles else Icons.Rounded.SubtitlesOff,
                        if (showLyrics) "Ocultar letra" else "Ver letra",
                        tint = if (showLyrics) Coral else Color.White.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = { prefs.setAudioRepeat((repeat + 1) % 3) }) {
                    Icon(
                        if (repeat == Prefs.REPEAT_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repetir",
                        tint = if (repeat == Prefs.REPEAT_OFF) Color.White.copy(alpha = 0.7f) else Coral,
                    )
                }
                // Subir el volumen de esta canción (por si se oye bajito); se recuerda para la próxima vez
                Box {
                    IconButton(onClick = { boostOpen = true }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.VolumeUp,
                            "Subir volumen",
                            tint = if (song.boostDb > 0) Coral else Color.White.copy(alpha = 0.7f),
                        )
                    }
                    DropdownMenu(
                        expanded = boostOpen,
                        onDismissRequest = { boostOpen = false },
                        containerColor = Color(0xFF161827),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        BoostPicker(
                            current = song.boostDb,
                            onPick = { onBoost(it) },
                            modifier = Modifier.width(290.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Text(
                when (repeat) {
                    Prefs.REPEAT_ONE -> "Repitiendo esta canción"
                    Prefs.REPEAT_ALL -> "Repitiendo la lista"
                    else -> "Sin repetir"
                } + (if (shuffle) " · aleatorio" else "") + (if (!showLyrics) " · letra oculta" else "") +
                    (if (song.boostDb > 0) " · volumen ${boostLabel(song.boostDb)}" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(22.dp))
            if (showLyrics) LyricsCard(
                lyrics = lyrics,
                loading = loadingLyrics,
                positionMs = position,
                showTranslation = showTranslation,
                onToggleTranslation = { showTranslation = !showTranslation },
                onSeek = { ms -> player.seekTo(ms); player.play() },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Fondo animado: manchas de color que se mueven despacio (más rápido mientras suena). */
@Composable
private fun AnimatedBackdrop(song: Video, playing: Boolean) {
    val context = LocalContext.current
    key(song.id, song.localThumbPath, song.thumbnailUrl) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(song.thumbModel()).crossfade(400).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(70.dp),
            alpha = 0.30f,
        )
    }
    val t = rememberInfiniteTransition(label = "backdrop")
    val a by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(if (playing) 14_000 else 30_000, easing = LinearEasing)), label = "a")
    val b by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(if (playing) 19_000 else 40_000, easing = LinearEasing)), label = "b")
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                fun blob(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(
                    Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r,
                    center = Offset(cx, cy),
                )
                blob(w * (0.5f + 0.35f * kotlin.math.cos(a)), h * (0.25f + 0.12f * kotlin.math.sin(a)), w * 0.75f, Coral.copy(alpha = 0.30f))
                blob(w * (0.5f + 0.40f * kotlin.math.sin(b)), h * (0.60f + 0.15f * kotlin.math.cos(b)), w * 0.80f, Color(0xFF6E4BD8).copy(alpha = 0.22f))
                blob(w * (0.5f - 0.30f * kotlin.math.cos(b)), h * (0.90f - 0.10f * kotlin.math.sin(a)), w * 0.70f, Color(0xFF2E8B7A).copy(alpha = 0.18f))
            }
            .background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.15f), Ink.copy(alpha = 0.75f)))),
    )
}

/** Letra de la canción, con la línea que suena resaltada y su traducción al español debajo. */
@Composable
private fun LyricsCard(
    lyrics: LyricsResult?,
    loading: Boolean,
    positionMs: Long,
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), strength = 1.1f)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Letra", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val hasTranslation = lyrics?.lines?.any { it.translation != null } == true
            if (hasTranslation) {
                Text(
                    if (showTranslation) "Ocultar traducción" else "Ver traducción",
                    style = MaterialTheme.typography.labelLarge,
                    color = Coral,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onToggleTranslation).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Buscando la letra…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            lyrics == null -> Text(
                "No encontramos la letra de esta canción.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            lyrics.synced -> {
                val lines = lyrics.lines
                val current = lines.indexOfLast { (it.timeMs ?: Long.MAX_VALUE) <= positionMs + 300 }
                val listState = rememberLazyListState()
                LaunchedEffect(current) {
                    if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(lines) { i, line ->
                        LyricRow(line, isCurrent = i == current, past = i < current, showTranslation = showTranslation) {
                            line.timeMs?.let(onSeek)
                        }
                    }
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lyrics.lines.forEach { line -> LyricRow(line, isCurrent = false, past = false, showTranslation = showTranslation) {} }
            }
        }
        if (lyrics != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Letra: LRCLIB · Letras.com · Traducción automática",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, isCurrent: Boolean, past: Boolean, showTranslation: Boolean, onClick: () -> Unit) {
    if (line.text.isBlank()) {
        Text("♪", color = Color.White.copy(alpha = 0.4f))
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 2.dp)) {
        Text(
            line.text,
            style = if (isCurrent) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyLarge,
            color = when {
                isCurrent -> Color.White
                past -> Color.White.copy(alpha = 0.45f)
                else -> Color.White.copy(alpha = 0.7f)
            },
        )
        if (showTranslation && line.translation != null) {
            Text(
                line.translation,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                color = if (isCurrent) Coral else Coral.copy(alpha = 0.6f),
            )
        }
    }
}
