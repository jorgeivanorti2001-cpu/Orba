package com.orba.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.orba.app.R
import com.orba.app.data.DownloadState
import com.orba.app.data.Video
import com.orba.app.data.isDownloaded
import com.orba.app.data.thumbModel
import com.orba.app.engine.StreamResolver
import com.orba.app.ui.CategoryFilter
import com.orba.app.ui.MainViewModel
import com.orba.app.ui.Tab
import com.orba.app.data.Kind
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import com.orba.app.ui.components.ManageCategoriesDialog
import com.orba.app.ui.formatTime
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.BrandGradient
import com.orba.app.ui.theme.Mint
import com.orba.app.ui.theme.NavClearance
import com.orba.app.ui.theme.Rose
import com.orba.app.ui.theme.Violet
import com.orba.app.ui.theme.OrbaBlue
import com.orba.app.ui.theme.OrbaCyan
import com.orba.app.ui.theme.OrbaMagenta
import com.orba.app.ui.theme.OrbaViolet
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import com.orba.app.ui.theme.glass
import java.util.Calendar

/** Pantalla de inicio: la antesala antes de empezar a ver videos. Aquí no suena nada. */
@OptIn(ExperimentalTextApi::class)
@Composable
fun HomeScreen(vm: MainViewModel, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val videos by vm.videos.collectAsStateWithLifecycle()
    val audios by vm.audios.collectAsStateWithLifecycle()
    val all by vm.allItems.collectAsStateWithLifecycle()
    val userName by vm.prefs.userName.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val audioCategories by vm.audioCategories.collectAsStateWithLifecycle()
    val showMusic by vm.showMusic.collectAsStateWithLifecycle()
    val noteCounts by vm.noteCounts.collectAsStateWithLifecycle()
    val maxHeight by vm.prefs.maxHeight.collectAsStateWithLifecycle()
    var managing by remember { mutableStateOf(false) }

    val continueVideo = remember(videos) { videos.filter { it.lastPlayedAt > 0 }.maxByOrNull { it.lastPlayedAt } }

    // Mientras estás aquí, se deja listo el primer video para que arranque sin espera
    LaunchedEffect(continueVideo?.id, videos.firstOrNull()?.id) {
        (continueVideo ?: videos.firstOrNull())?.takeIf { !it.isDownloaded() }?.let {
            StreamResolver.prefetch(context, it.sourceUrl, maxHeight, warm = true)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = NavClearance),
    ) {
        Spacer(Modifier.height(18.dp))

        // Encabezado: un solo logo, la marca y el saludo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.orba_emblem), null, Modifier.size(60.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // El nombre con la misma letra del logo (la A sin travesaño)
                Image(painterResource(R.drawable.orba_wordmark), "Orba", Modifier.height(17.dp).aspectRatio(6.65f))
                Spacer(Modifier.height(6.dp))
                Text(
                    greeting() + (userName?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Agregar: pegar enlaces o buscar en internet
            Box {
                var addMenu by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(BrandGradient))
                        .clickable { addMenu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "Agregar", tint = Color.White) }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    if (vm.showVideo) DropdownMenuItem(
                        text = { Text("Pegar enlaces de videos") },
                        leadingIcon = { Icon(Icons.Rounded.SmartDisplay, null) },
                        onClick = { addMenu = false; vm.openAdd(Kind.VIDEO) },
                    )
                    if (showMusic) DropdownMenuItem(
                        text = { Text("Pegar enlaces de canciones") },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, null) },
                        onClick = { addMenu = false; vm.openAdd(Kind.AUDIO) },
                    )
                    if (vm.showVideo) DropdownMenuItem(
                        text = { Text("Buscar videos en internet") },
                        leadingIcon = { Icon(Icons.Rounded.TravelExplore, null) },
                        onClick = { addMenu = false; vm.openWebSearch(Kind.VIDEO) },
                    )
                    if (showMusic) DropdownMenuItem(
                        text = { Text("Buscar canciones en internet") },
                        leadingIcon = { Icon(Icons.Rounded.TravelExplore, null) },
                        onClick = { addMenu = false; vm.openWebSearch(Kind.AUDIO) },
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        // Eslogan de la marca
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("TU CONTENIDO.\n") }
                withStyle(SpanStyle(brush = Brush.horizontalGradient(listOf(OrbaCyan, OrbaBlue, OrbaViolet, OrbaMagenta)))) { append("TU RITMO.") }
            },
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Tus videos y tu música a tu manera, sin algoritmos, sin anuncios, siempre contigo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        // Seguir donde quedaste (solo si hay algo a medias)
        continueVideo?.takeIf { vm.showVideo }?.let { v ->
            ContinueCard(v) { vm.openVideo(v.id) }
            Spacer(Modifier.height(14.dp))
        }

        // Accesos que no están en la barra de abajo
        val saved = all.count { it.downloadState == DownloadState.DONE }
        val tilesSpec = buildList {
            add(TileSpec(Icons.Rounded.CloudDone, "$saved", "sin conexión", Mint) { vm.offlineOpen.value = true })
            if (showMusic) add(TileSpec(Icons.Rounded.Favorite, "${audios.count { it.favorite }}", "me encanta", Rose) {
                vm.audioFilter.value = CategoryFilter.Favorites
                vm.tab.value = Tab.LISTEN
            })
            if (vm.showVideo) add(TileSpec(Icons.AutoMirrored.Rounded.Notes, "${noteCounts.values.sum()}", "notas", Coral) { vm.openLibrary(Kind.VIDEO, notesOnly = true) })
        }
        tilesSpec.chunked(2).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> StatTile(t.icon, t.value, t.label, t.accent, Modifier.weight(1f), t.onClick) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(26.dp))

        // En Orba: categorías de video. En Orba Music: playlists.
        val music = vm.musicOnly
        val shownCategories = if (music) audioCategories else categories
        val items = if (music) audios else videos
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (music) "Tus playlists" else "Categorías de video", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable { managing = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Tune, null, tint = Coral, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Organizar", style = MaterialTheme.typography.labelLarge, color = Coral)
            }
        }
        Spacer(Modifier.height(12.dp))

        val open: (CategoryFilter) -> Unit = { f ->
            if (music) {
                vm.audioFilter.value = f
                vm.tab.value = Tab.LISTEN
            } else vm.openFeed(f)
        }
        val tiles = buildList {
            add(Triple<String, Color, CategoryFilter>("Todos", Coral, CategoryFilter.All) to items.size)
            shownCategories.forEach { c ->
                add(Triple<String, Color, CategoryFilter>(c.name, Color(c.color), CategoryFilter.Only(c.id)) to items.count { it.categoryId == c.id })
            }
            val none = items.count { it.categoryId == null }
            if (none > 0 && shownCategories.isNotEmpty()) add(Triple<String, Color, CategoryFilter>(if (music) "Sin playlist" else "Sin categoría", Color(0xFF8A90A0), CategoryFilter.None) to none)
        }
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (info, count) ->
                    CategoryTile(info.first, count, info.second, Modifier.weight(1f)) { open(info.third) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        if (shownCategories.isEmpty()) {
            Text(
                if (music) "Crea playlists con «Organizar» para agrupar tus canciones." else "Crea categorías con «Organizar» para separar tus videos por temas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (managing) {
        ManageCategoriesDialog(
            categories = if (vm.musicOnly) audioCategories else categories,
            onCreate = { vm.createCategory(it, kind = if (vm.musicOnly) Kind.AUDIO else Kind.VIDEO) },
            onRename = { id, name -> vm.renameCategory(id, name) },
            onDelete = { vm.deleteCategory(it) },
            onDismiss = { managing = false },
        )
    }
}

private class TileSpec(val icon: ImageVector, val value: String, val label: String, val accent: Color, val onClick: () -> Unit)

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

@Composable
private fun ContinueCard(video: Video, onPlay: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .glass(RoundedCornerShape(26.dp))
            .clickable(onClick = onPlay),
    ) {
        AsyncImage(
            model = video.thumbModel(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.85f,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.85f)))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp).padding(end = 70.dp)) {
            Text("Continuar viendo", style = MaterialTheme.typography.labelLarge, color = Coral)
            Spacer(Modifier.height(4.dp))
            Text(video.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (video.durationSec > 0 && video.lastPositionMs > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (video.lastPositionMs / 1000f / video.durationSec).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                    color = Coral,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatTime(video.lastPositionMs)} de ${formatTime(video.durationSec * 1000L)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .size(54.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(BrandGradient)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PlayArrow, "Reproducir", tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun WelcomeCard(hasVideos: Boolean, onAdd: () -> Unit, onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp), strength = 1.2f)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(Coral.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.7f),
                    radius = size.width * 0.7f,
                    center = Offset(size.width, 0f),
                )
            }
            .clickable(onClick = if (hasVideos) onStart else onAdd)
            .padding(22.dp),
    ) {
        Text(
            if (hasVideos) "Tu lista te espera" else "Tu Orba está lista",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasVideos) "Desliza entre tus videos, sin algoritmo: solo lo que tú elegiste."
            else "Agrega tus primeros enlaces de YouTube, Facebook, Instagram o TikTok. Se guardan en el teléfono para que nunca se pierdan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .glass(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.35f))
    }
}

/** Tarjeta de bienvenida con el logo y accesos a Ver y Escuchar. */
@Composable
private fun BrandHero(showVideo: Boolean, showMusic: Boolean, onWatch: () -> Unit, onListen: () -> Unit, onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp), strength = 1.2f)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(Coral.copy(alpha = 0.40f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.8f),
                    radius = size.width * 0.8f,
                    center = Offset(size.width, 0f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(Violet.copy(alpha = 0.25f), Color.Transparent), center = Offset(0f, size.height), radius = size.width * 0.6f),
                    radius = size.width * 0.6f,
                    center = Offset(0f, size.height),
                )
            }
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.orba_emblem), null, Modifier.size(72.dp))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).glass(CircleShape, strength = 1.5f).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Agregar", tint = Color.White) }
        }
        Spacer(Modifier.height(18.dp))
        Text("Inicia tu experiencia hoy", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                !showVideo -> "Tu música, a tu manera. Sin algoritmos ni anuncios, siempre contigo, incluso sin conexión."
                !showMusic -> "Tus videos, a tu manera. Sin algoritmos ni anuncios, siempre contigo, incluso sin conexión."
                else -> "Tus videos y tu música, a tu manera. Sin algoritmos, sin anuncios, siempre contigo."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showVideo) Row(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(BrandGradient))
                    .clickable(onClick = onWatch),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SmartDisplay, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Ver", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            if (showMusic) Row(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .then(
                        if (showVideo) Modifier.glass(RoundedCornerShape(16.dp), strength = 1.5f)
                        else Modifier.clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(BrandGradient))
                    )
                    .clickable(onClick = onListen),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Headphones, null, tint = if (showVideo) Coral else Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Escuchar", style = MaterialTheme.typography.titleMedium, color = if (showVideo) Color.White else Color.White)
            }
        }
    }
}

@Composable
private fun CategoryTile(name: String, count: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(96.dp)
            .glass(RoundedCornerShape(22.dp))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = Offset(size.width, size.height), radius = size.height * 1.2f),
                    radius = size.height * 1.2f,
                    center = Offset(size.width, size.height),
                )
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
