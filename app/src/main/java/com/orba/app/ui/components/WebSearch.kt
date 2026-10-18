package com.orba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.orba.app.data.Kind
import com.orba.app.engine.Engine
import com.orba.app.ui.formatDuration
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.BrandGradient
import com.orba.app.ui.theme.Mint
import com.orba.app.ui.theme.Rose
import com.orba.app.ui.theme.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class AddState { ADDING, ADDED }

/**
 * Buscador en internet: si la canción o el video no está en tu lista, se busca en YouTube
 * y se agrega con el botón "+", como los demás (sin reproducirlo).
 */
@Composable
fun WebSearchSection(
    query: String,
    kind: Int,
    existingUrls: Set<String>,
    onSearch: suspend (String) -> List<Engine.SearchResult>,
    onAdd: suspend (Engine.SearchResult) -> Long?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var searched by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Engine.SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val added = remember { mutableStateMapOf<String, AddState>() }
    val q = query.trim()

    Column(modifier.fillMaxWidth()) {
        if (q.isNotEmpty() && q != searched) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(16.dp), strength = 1.4f, tint = Coral)
                    .clickable(enabled = !loading) {
                        loading = true
                        error = null
                        searched = q
                        scope.launch {
                            try {
                                results = onSearch(q)
                                if (results.isEmpty()) error = "No se encontró nada"
                            } catch (e: CancellationException) {
                                throw e
                            } catch (t: Throwable) {
                                results = emptyList()
                                error = Engine.friendlyError(t)
                            }
                            loading = false
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.TravelExplore, null, tint = Coral)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Buscar «$q» en internet",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (kind == Kind.AUDIO) "Si no está en tu lista, agrégala desde aquí" else "Si no está en tu lista, agrégalo desde aquí",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (loading) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Coral)
                Spacer(Modifier.width(10.dp))
                Text("Buscando en YouTube…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.takeIf { !loading }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }

        if (!loading && results.isNotEmpty()) {
            Text(
                "En internet · ${searched.orEmpty()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                results.forEach { r ->
                    val state = added[r.url] ?: if (r.url in existingUrls) AddState.ADDED else null
                    ResultRow(r, kind, state) {
                        if (state != null) return@ResultRow
                        added[r.url] = AddState.ADDING
                        scope.launch {
                            val id = onAdd(r)
                            if (id != null) added[r.url] = AddState.ADDED else added.remove(r.url)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: Engine.SearchResult, kind: Int, state: AddState?, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onAdd)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = if (kind == Kind.AUDIO) 52.dp else 84.dp, height = 52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            AsyncImage(r.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(r.uploader, formatDuration(r.durationSec).ifEmpty { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        AddState.ADDED -> Brush.linearGradient(listOf(Mint.copy(alpha = 0.25f), Mint.copy(alpha = 0.25f)))
                        else -> Brush.linearGradient(BrandGradient)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                AddState.ADDING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                AddState.ADDED -> Icon(Icons.Rounded.Check, "Agregado", tint = Mint, modifier = Modifier.size(20.dp))
                null -> Icon(Icons.Rounded.Add, "Agregar", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}
