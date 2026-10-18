package com.orba.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orba.app.ui.MainViewModel
import com.orba.app.ui.formatSize
import com.orba.app.ui.lock.License
import com.orba.app.ui.components.TextInputDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.orba.app.R
import com.orba.app.data.Prefs
import com.orba.app.ui.theme.NavClearance
import com.orba.app.ui.theme.glass
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val prefs = vm.prefs
    val maxHeight by prefs.maxHeight.collectAsStateWithLifecycle()
    val auto by prefs.autoDownload.collectAsStateWithLifecycle()
    val wifi by prefs.wifiOnly.collectAsStateWithLifecycle()
    val loop by prefs.loop.collectAsStateWithLifecycle()
    val audioOnly by prefs.audioOnly.collectAsStateWithLifecycle()
    val version by vm.engineVersion.collectAsStateWithLifecycle()
    val updating by vm.engineUpdating.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()

    val userName by prefs.userName.collectAsStateWithLifecycle()
    val autoAudio by prefs.autoDownloadAudio.collectAsStateWithLifecycle()
    val wifiAudio by prefs.wifiOnlyAudio.collectAsStateWithLifecycle()
    val audioQuality by prefs.audioQuality.collectAsStateWithLifecycle()
    val spanish by prefs.spanishAudio.collectAsStateWithLifecycle()
    val showMusic by vm.showMusic.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val unified by prefs.unified.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadEngineVersion() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = NavClearance),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))

        Section("Perfil") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tu nombre", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        userName?.takeIf { it.isNotBlank() } ?: "Sin nombre",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { editingName = true }) { Text("Cambiar") }
            }
            // Correo de Google para el respaldo de la lista
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.08f))
            Text("Correo y respaldo", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            BackupContent(vm)
        }

        if (vm.showVideo) Section("Videos") {
            Text("Calidad", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Calidad máxima al ver en línea y al descargar. Menos calidad gasta menos datos y espacio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            val options = listOf(480, 720, 1080)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, h ->
                    SegmentedButton(
                        selected = maxHeight == h,
                        onClick = { prefs.setMaxHeight(h) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text("${h}p") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Toggle(
                "Descargar al agregar",
                "Cada video nuevo se guarda en el teléfono. Así sigue disponible aunque lo borren de la plataforma.",
                auto,
            ) { prefs.setAutoDownload(it) }
            Toggle("Solo con Wi-Fi", "Los videos esperan a tener Wi-Fi para descargarse.", wifi) { prefs.setWifiOnly(it) }
            Toggle(
                "Repetir en bucle",
                "El video que suena se repite solo, hasta ${Prefs.MAX_PLAYS} veces. Si lo apagas, se detiene al terminar.",
                loop,
            ) { prefs.setLoop(it) }
            Toggle(
                "Solo audio",
                "Escucha los videos como si fueran música: no carga la imagen, ahorra batería y datos.",
                audioOnly,
            ) { prefs.setAudioOnly(it) }
        }

        if (showMusic) Section("Música") {
            Text("Calidad del audio", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Ahorro pesa cerca de la mitad y para escuchar con audífonos casi no se nota.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            val aq = listOf("Normal", "Ahorro")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                aq.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = audioQuality == i,
                        onClick = { prefs.setAudioQuality(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, aq.size),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Toggle(
                "Descargar al agregar",
                "Cada canción nueva se guarda en el teléfono para escucharla sin conexión.",
                autoAudio,
            ) { prefs.setAutoDownloadAudio(it) }
            Toggle("Solo con Wi-Fi", "Las canciones esperan a tener Wi-Fi para descargarse.", wifiAudio) { prefs.setWifiOnlyAudio(it) }
        }

        Section("Idioma") {
            Toggle(
                "Preferir audio en español",
                "Si la plataforma ofrece el video doblado al español (como los doblajes automáticos de YouTube), se usa esa pista. Aplica a lo que veas o descargues de aquí en adelante.",
                spanish,
            ) {
                prefs.setSpanishAudio(it)
                com.orba.app.engine.Engine.preferSpanish = it
            }
            Text(
                "Espacio usado por descargas: ${formatSize(allItems.sumOf { it.fileSizeBytes })}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (License.isAdmin(context)) Section("Acceso") { AccessContent(vm) }

        Section("Motor de extracción") {
            Text(
                "YouTube y Facebook cambian seguido. Si un enlace deja de funcionar, actualiza el motor. Se actualiza solo cada 3 días.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Versión: ${version ?: "…"}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { vm.updateEngine() }, enabled = !updating, shape = RoundedCornerShape(12.dp)) {
                    if (updating) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (updating) "Actualizando" else "Actualizar")
                }
            }
        }

        Text(
            "${stringResource(R.string.app_name)} ${com.orba.app.BuildConfig.VERSION_NAME} · Uso personal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }


    if (editingName) {
        TextInputDialog(
            title = "Tu nombre",
            initial = userName.orEmpty(),
            confirm = "Guardar",
            onConfirm = { prefs.setUserName(it); editingName = false },
            onDismiss = { editingName = false },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
