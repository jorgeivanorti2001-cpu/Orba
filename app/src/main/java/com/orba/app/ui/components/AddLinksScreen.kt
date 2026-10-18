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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.orba.app.data.Category
import com.orba.app.engine.Engine
import com.orba.app.ui.AddQueue
import com.orba.app.ui.LinkStatus
import com.orba.app.ui.PendingLink
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.BrandGradient
import com.orba.app.ui.theme.Mint
import com.orba.app.ui.theme.Rose
import com.orba.app.ui.theme.appBackground
import com.orba.app.ui.theme.blockTouches
import com.orba.app.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Agregar uno o varios videos o canciones. Cada enlace que pegas (o que copias en otra app y
 * vuelves) se suma solo a la lista y se empieza a reconocer de una vez. Al tocar «Agregar»
 * la pantalla se cierra y todo termina en segundo plano; al final llega un aviso.
 * Lo que ya está en tu lista se marca en amarillo y se puede quitar con la X.
 */
@Composable
fun AddLinksScreen(
    audio: Boolean,
    links: List<PendingLink>,
    categories: List<Category>,
    defaultCategory: Long?,
    onAddText: (String) -> Int,
    onRemove: (String) -> Unit,
    onSubmit: (Long?) -> Unit,
    onCreateCategory: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(defaultCategory) }
    var newCategory by remember { mutableStateOf(false) }
    var resumes by remember { mutableIntStateOf(0) }

    fun take(raw: String): Boolean {
        val n = onAddText(raw)
        if (n > 0) scope.launch { delay(80); listState.animateScrollToItem(maxOf(0, links.size + n - 1)) }
        return Engine.extractUrls(raw).isNotEmpty()
    }

    // Al abrir y al volver de otra app: si copiaste un enlace nuevo, entra solo a la lista
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    LaunchedEffect(resumes) {
        delay(350) // Android solo deja leer el portapapeles con la app en primer plano
        val clip = runCatching { clipboard.getText()?.text }.getOrNull()
        // Lo que ya se tomó del portapapeles no vuelve a entrar al abrir otra vez esta pantalla
        if (clip != null && clip != AddQueue.lastClip && Engine.extractUrls(clip).isNotEmpty()) {
            AddQueue.lastClip = clip
            take(clip)
        }
    }

    val pending = links.count { it.status != LinkStatus.DUPLICATE }
    val repeated = links.count { it.status == LinkStatus.DUPLICATE }
    val checking = links.count { it.status == LinkStatus.CHECKING }

    Box(Modifier.fillMaxSize().appBackground().blockTouches()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (audio) "Agregar canciones" else "Agregar videos", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        when {
                            links.isEmpty() -> if (audio) "Se guarda solo el audio de cada enlace" else "Pega uno o varios enlaces"
                            repeated > 0 -> "${links.size} en la lista · $repeated ya estaban"
                            else -> "${links.size} en la lista"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cerrar") }
            }

            Spacer(Modifier.height(14.dp))

            // Campo para pegar: en cuanto detecta un enlace, lo pasa a la lista y se limpia
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(20.dp), strength = 1.3f)
                    .padding(start = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = text,
                    onValueChange = { value -> text = if (take(value)) "" else value },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Pega aquí el enlace…") },
                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (take(text)) text = "" }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Coral.copy(alpha = 0.18f))
                        .clickable {
                            val clip = clipboard.getText()?.text
                            if (clip != null) {
                                AddQueue.lastClip = clip
                                take(clip)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ContentPaste, null, tint = Coral, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pegar", style = MaterialTheme.typography.labelLarge, color = Coral)
                }
            }

            Spacer(Modifier.height(14.dp))

            if (links.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(72.dp).glass(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Link, null, tint = Coral, modifier = Modifier.size(32.dp)) }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Copia enlaces en YouTube, Facebook, Instagram o TikTok y vuelve aquí: cada enlace nuevo aparece solo en la lista. También puedes pegar varios de una vez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(links, key = { _, l -> l.url }) { i, link ->
                        LinkRow(i + 1, link) { onRemove(link.url) }
                    }
                }
            }

            // Categoría y botón final, siempre visibles encima del teclado
            Column(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), strength = 1.2f)
                    .padding(14.dp),
            ) {
                Text(if (audio) "Playlist" else "Categoría", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                CategorySelector(categories, categoryId, { categoryId = it }, { newCategory = true })
                Spacer(Modifier.height(14.dp))
                val enabled = pending > 0
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (enabled) Brush.horizontalGradient(BrandGradient)
                            else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
                        )
                        .clickable(enabled = enabled) { onSubmit(categoryId) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            pending == 0 && repeated > 0 -> "Quita los repetidos con la X"
                            pending == 0 -> "Agrega enlaces a la lista"
                            pending == 1 -> if (audio) "Agregar 1 canción" else "Agregar 1 video"
                            else -> if (audio) "Agregar $pending canciones" else "Agregar $pending videos"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (enabled && (checking > 0 || repeated > 0)) {
                    Text(
                        listOfNotNull(
                            if (checking > 0) "No hace falta esperar: se terminan de reconocer en segundo plano" else null,
                            if (repeated > 0) "los repetidos no se agregan" else null,
                        ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }

    if (newCategory) {
        TextInputDialog(
            title = "Nueva categoría",
            initial = "",
            confirm = "Crear",
            onConfirm = { onCreateCategory(it); newCategory = false },
            onDismiss = { newCategory = false },
        )
    }
}

@Composable
private fun LinkRow(index: Int, link: PendingLink, onRemove: () -> Unit) {
    val warn = Color(0xFFFFC857)
    val tint = when (link.status) {
        LinkStatus.READY -> Mint
        LinkStatus.DUPLICATE -> warn
        LinkStatus.ERROR -> MaterialTheme.colorScheme.error
        LinkStatus.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(
                RoundedCornerShape(16.dp),
                tint = when (link.status) {
                    LinkStatus.ERROR -> MaterialTheme.colorScheme.error
                    LinkStatus.DUPLICATE -> warn
                    else -> Color.White
                },
            )
            .padding(start = 12.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            when (link.status) {
                LinkStatus.CHECKING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                LinkStatus.READY -> Icon(Icons.Rounded.CheckCircle, "Listo", tint = Mint, modifier = Modifier.size(20.dp))
                LinkStatus.DUPLICATE -> Icon(Icons.Rounded.Info, "Repetido", tint = warn, modifier = Modifier.size(20.dp))
                LinkStatus.ERROR -> Icon(Icons.Rounded.ErrorOutline, "Error", tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                link.title ?: "$index · " + Engine.platformLabel(Engine.detectPlatform(link.url)),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (link.status) {
                    LinkStatus.CHECKING -> "Reconociendo…"
                    LinkStatus.READY -> link.url.removePrefix("https://").removePrefix("www.")
                    else -> link.message ?: link.url.removePrefix("https://").removePrefix("www.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (link.status == LinkStatus.READY) MaterialTheme.colorScheme.onSurfaceVariant else tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Close,
                "Quitar de la lista",
                tint = if (link.status == LinkStatus.DUPLICATE) warn else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
