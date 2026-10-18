package com.orba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orba.app.data.Category
import com.orba.app.data.DownloadState
import com.orba.app.data.Video
import com.orba.app.ui.CategoryFilter
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.BrandGradient
import com.orba.app.ui.theme.Mint
import com.orba.app.ui.theme.darkGlass
import com.orba.app.ui.theme.glass

/** Fila de categorías en forma de "píldoras" de vidrio. */
@Composable
fun CategoryChips(
    categories: List<Category>,
    filter: CategoryFilter,
    onFilter: (CategoryFilter) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    /** Muestra la playlist especial "Me encanta" (favoritos). */
    showFavorites: Boolean = false,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { GlassChip("Todos", filter == CategoryFilter.All, null, onDark) { onFilter(CategoryFilter.All) } }
        if (showFavorites) item {
            GlassChip("♥ Me encanta", filter == CategoryFilter.Favorites, null, onDark) { onFilter(CategoryFilter.Favorites) }
        }
        items(categories, key = { it.id }) { c ->
            GlassChip(c.name, filter == CategoryFilter.Only(c.id), Color(c.color), onDark) { onFilter(CategoryFilter.Only(c.id)) }
        }
        item { GlassChip("Sin categoría", filter == CategoryFilter.None, null, onDark) { onFilter(CategoryFilter.None) } }
    }
}

@Composable
fun GlassChip(label: String, selected: Boolean, dot: Color?, onDark: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = when {
        selected -> Modifier
            .clip(shape)
            .background(Brush.horizontalGradient(BrandGradient))
        onDark -> Modifier.darkGlass(shape, alpha = 0.35f)
        else -> Modifier.glass(shape)
    }
    Row(
        base
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            ColorDot(if (selected) Color.White else dot)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}

@Composable
fun ColorDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Cómo se ve el botón de descarga según el estado del video. */
data class DownloadVisual(val icon: ImageVector, val label: String, val progress: Float?, val tint: Color?)

@Composable
fun downloadVisual(video: Video): DownloadVisual = when (video.downloadState) {
    DownloadState.QUEUED -> DownloadVisual(Icons.Rounded.Schedule, "En cola", null, null)
    DownloadState.DOWNLOADING -> DownloadVisual(
        Icons.Rounded.Download, "${(video.downloadProgress * 100).toInt()}%", video.downloadProgress, null,
    )
    DownloadState.DONE -> DownloadVisual(Icons.Rounded.CloudDone, "Guardado", null, Mint)
    DownloadState.FAILED -> DownloadVisual(Icons.Rounded.ErrorOutline, "Reintentar", null, MaterialTheme.colorScheme.error)
    else -> DownloadVisual(Icons.Rounded.Download, "Descargar", null, null)
}

/** Botón redondo de vidrio con etiqueta, usado en las acciones de cada video. */
@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    progress: Float? = null,
    active: Boolean = false,
    onDark: Boolean = false,
    badge: Int = 0,
    /** Versión más pequeña, para que en la vista vertical el video se vea más limpio. */
    compact: Boolean = false,
) {
    val circleSize = if (compact) 36.dp else 42.dp
    val iconSize = if (compact) 18.dp else 20.dp
    val fg = tint ?: if (active) Color.White else Color.White
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val circle = when {
                active -> Modifier.size(circleSize).clip(CircleShape).background(Coral)
                onDark -> Modifier.size(circleSize).darkGlass(CircleShape, alpha = 0.35f)
                else -> Modifier.size(circleSize).glass(CircleShape)
            }
            Box(circle, contentAlignment = Alignment.Center) {
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(circleSize - 4.dp),
                        strokeWidth = 2.5.dp,
                        trackColor = Color.White.copy(alpha = 0.12f),
                    )
                }
                Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(iconSize))
            }
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(if (compact) 15.dp else 17.dp)
                        .clip(CircleShape)
                        .background(Coral),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (badge > 9) "9+" else badge.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp) else MaterialTheme.typography.labelSmall,
            color = if (onDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 72.dp),
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Color.Black.copy(alpha = 0.55f), icon: ImageVector? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}
