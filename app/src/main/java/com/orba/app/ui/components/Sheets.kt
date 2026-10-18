package com.orba.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orba.app.data.Category
import com.orba.app.data.DownloadState
import com.orba.app.data.Video
import com.orba.app.engine.Engine
import com.orba.app.data.Orientation
import com.orba.app.data.Kind
import com.orba.app.ui.theme.SheetColor

// ---------------- Selector de categoría ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySelector(
    categories: List<Category>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onNew: () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassChip("Ninguna", selected == null, null) { onSelect(null) }
        categories.forEach { c ->
            GlassChip(c.name, selected == c.id, Color(c.color)) { onSelect(c.id) }
        }
        GlassChip("+ Nueva", false, null, onClick = onNew)
    }
}

// ---------------- Opciones de un video ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsSheet(
    video: Video,
    categories: List<Category>,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onSetCategory: (Long?) -> Unit,
    onCreateCategory: (String) -> Unit,
    onRename: (String) -> Unit,
    onSetOrientation: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** Sección a la que se puede pasar ("Escuchar" o "Ver"); null si la app no tiene las dos. */
    transferTarget: String? = null,
    onCopyToOther: () -> Unit = {},
    onMoveToOther: () -> Unit = {},
    /** Escuchar: sugerir el género de esta canción con IA. */
    onSuggestGenre: (() -> Unit)? = null,
    /** Subir el volumen de este video o canción (se recuerda). */
    onSetBoost: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val local = video.platform == Engine.PLATFORM_LOCAL
    val clipboard = LocalClipboardManager.current
    var dialog by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            Text(
                video.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (onSetBoost != null) {
                BoostPicker(
                    current = video.boostDb,
                    onPick = onSetBoost,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 10.dp),
                )
            }
            val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
            if (!local) when (video.downloadState) {
                DownloadState.DONE -> ListItem(
                    headlineContent = { Text("Quitar descarga") },
                    supportingContent = { Text("Libera espacio; el video sigue en tu lista") },
                    leadingContent = { Icon(Icons.Rounded.CloudDone, null) },
                    colors = itemColors,
                    modifier = Modifier.clickable { onDeleteDownload(); onDismiss() },
                )
                DownloadState.QUEUED, DownloadState.DOWNLOADING -> ListItem(
                    headlineContent = { Text("Cancelar descarga") },
                    leadingContent = { Icon(Icons.Rounded.Close, null) },
                    colors = itemColors,
                    modifier = Modifier.clickable { onCancelDownload(); onDismiss() },
                )
                else -> ListItem(
                    headlineContent = { Text("Descargar para ver sin conexión") },
                    leadingContent = { Icon(Icons.Rounded.Download, null) },
                    colors = itemColors,
                    modifier = Modifier.clickable { onDownload(); onDismiss() },
                )
            }
            if (transferTarget != null && !local) {
                val toAudio = video.kind != Kind.AUDIO
                ListItem(
                    headlineContent = { Text("Agregar también a $transferTarget") },
                    supportingContent = { Text(if (toAudio) "Queda aquí y además como audio" else "Queda aquí y además como video") },
                    leadingContent = { Icon(if (toAudio) Icons.Rounded.Headphones else Icons.Rounded.SmartDisplay, null) },
                    colors = itemColors,
                    modifier = Modifier.clickable { onCopyToOther(); onDismiss() },
                )
                ListItem(
                    headlineContent = { Text("Mover a $transferTarget") },
                    supportingContent = { Text("Se quita de aquí; las notas se conservan") },
                    leadingContent = { Icon(Icons.Rounded.SwapHoriz, null) },
                    colors = itemColors,
                    modifier = Modifier.clickable { onMoveToOther(); onDismiss() },
                )
            }
            if (video.kind != Kind.AUDIO) ListItem(
                headlineContent = { Text("Mostrar como") },
                supportingContent = {
                    Text(
                        if (video.orientation == Orientation.HORIZONTAL) "Horizontal, con controles abajo"
                        else "Vertical, pantalla completa"
                    )
                },
                leadingContent = { Icon(Icons.Rounded.ScreenRotation, null) },
                colors = itemColors,
                modifier = Modifier.clickable { dialog = "orientation" },
            )
            ListItem(
                headlineContent = { Text(if (video.kind == Kind.AUDIO) "Cambiar playlist" else "Cambiar categoría") },
                supportingContent = { Text(categories.firstOrNull { it.id == video.categoryId }?.name ?: "Sin categoría") },
                leadingContent = { Icon(Icons.Rounded.Folder, null) },
                colors = itemColors,
                modifier = Modifier.clickable { dialog = "category" },
            )
            if (onSuggestGenre != null) ListItem(
                headlineContent = { Text("Sugerir género (IA)") },
                supportingContent = { Text("Busca el género y lo pasa a esa playlist") },
                leadingContent = { Icon(Icons.Rounded.AutoAwesome, null) },
                colors = itemColors,
                modifier = Modifier.clickable { onSuggestGenre(); onDismiss() },
            )
            ListItem(
                headlineContent = { Text("Cambiar nombre") },
                leadingContent = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                colors = itemColors,
                modifier = Modifier.clickable { dialog = "rename" },
            )
            if (!local) ListItem(
                headlineContent = { Text("Abrir en ${Engine.platformLabel(video.platform)}") },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                colors = itemColors,
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.sourceUrl))) }
                    onDismiss()
                },
            )
            if (!local) ListItem(
                headlineContent = { Text("Copiar enlace") },
                leadingContent = { Icon(Icons.Rounded.Link, null) },
                colors = itemColors,
                modifier = Modifier.clickable { clipboard.setText(AnnotatedString(video.sourceUrl)); onDismiss() },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ListItem(
                headlineContent = { Text("Eliminar de mi lista", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Borra el video, su descarga y sus notas") },
                leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                colors = itemColors,
                modifier = Modifier.clickable { dialog = "delete" },
            )
        }
    }

    when (dialog) {
        "category" -> CategoryPickerDialog(
            categories = categories,
            current = video.categoryId,
            onPick = { onSetCategory(it); dialog = null },
            onCreate = { onCreateCategory(it); dialog = null },
            onDismiss = { dialog = null },
        )
        "orientation" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Mostrar como") },
            text = {
                Column {
                    val horizontal = video.orientation == Orientation.HORIZONTAL
                    PickRow("Vertical (pantalla completa)", !horizontal, null) { onSetOrientation(Orientation.AUTO); dialog = null }
                    PickRow("Horizontal (con controles abajo)", horizontal, null) { onSetOrientation(Orientation.HORIZONTAL); dialog = null }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cerrar") } },
        )
        "rename" -> TextInputDialog(
            title = "Nombre del video",
            initial = video.title,
            confirm = "Guardar",
            onConfirm = { onRename(it); dialog = null },
            onDismiss = { dialog = null },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("¿Eliminar este video?") },
            text = { Text("Se borrará de tu lista junto con la descarga y las notas. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); dialog = null; onDismiss() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
        )
    }
}

// ---------------- Diálogos ----------------

@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    current: Long?,
    onPick: (Long?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categoría") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    item {
                        PickRow("Sin categoría", current == null, null) { onPick(null) }
                    }
                    items(categories, key = { it.id }) { c ->
                        PickRow(c.name, current == c.id, Color(c.color)) { onPick(c.id) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Nueva categoría") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { if (newName.isNotBlank()) onCreate(newName) }, enabled = newName.isNotBlank()) {
                        Icon(Icons.Rounded.Add, "Crear")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun PickRow(label: String, selected: Boolean, color: Color?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        if (color != null) {
            ColorDot(color, 10.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ManageCategoriesDialog(
    categories: List<Category>,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorías") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                if (categories.isEmpty()) item {
                    Text("Aún no tienes categorías.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(categories, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(Color(c.color), 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { editing = c }) { Icon(Icons.Rounded.Edit, "Renombrar") }
                        IconButton(onClick = { onDelete(c.id) }) { Icon(Icons.Rounded.DeleteOutline, "Eliminar") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("Nueva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
    editing?.let { c ->
        TextInputDialog("Renombrar", c.name, "Guardar", { onRename(c.id, it); editing = null }, { editing = null })
    }
    if (creating) {
        TextInputDialog("Nueva categoría", "", "Crear", { onCreate(it); creating = false }, { creating = false })
    }
}
