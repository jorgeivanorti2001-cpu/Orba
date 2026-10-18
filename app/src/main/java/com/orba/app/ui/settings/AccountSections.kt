package com.orba.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orba.app.backup.CloudBackup
import com.orba.app.ui.MainViewModel
import com.orba.app.ui.lock.GrantAccessDialog
import com.orba.app.ui.lock.GrantedDevice
import com.orba.app.ui.lock.GrantedDevices
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.Mint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale("es", "CO")).format(Date(ms))

/**
 * Respaldo con tu cuenta de Google: la lista se guarda sola en tu Drive.
 * En un teléfono nuevo, al conectar la misma cuenta, todo vuelve (las descargas se repiten).
 */
@Composable
fun BackupContent(vm: MainViewModel) {
    val context = LocalContext.current
    val prefs = vm.prefs
    val email by prefs.backupEmail.collectAsStateWithLifecycle()
    val last by prefs.lastBackupAt.collectAsStateWithLifecycle()
    val needsLogin by prefs.backupNeedsLogin.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmOff by remember { mutableStateOf(false) }

    // Con el permiso en la mano: primero se trae lo que haya en Drive (nunca se borra nada),
    // luego se sube la lista completa. La cuenta solo queda conectada si Drive respondió bien.
    suspend fun withToken(token: String) {
        busy = "Conectando…"
        val mail = CloudBackup.email(token)
        busy = "Buscando tu respaldo…"
        val restoreError = runCatching { CloudBackup.restore(context, token) }
        val recovered = restoreError.getOrElse { -1 }
        busy = "Guardando…"
        val error = CloudBackup.backupNow(context, token)
        busy = null
        if (error == null || recovered >= 0) {
            prefs.setBackupEmail(mail ?: "Cuenta de Google")
            prefs.setBackupNeedsLogin(false)
        }
        message = when {
            error != null -> error
            recovered > 0 -> "Se recuperaron $recovered de tu respaldo. Para verlos sin conexión, vuelve a descargarlos."
            else -> "Listo: tu lista quedó respaldada en tu Google Drive"
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            busy = null
            message = "No se completó la conexión con Google"
            return@rememberLauncherForActivityResult
        }
        when (val auth = CloudBackup.resultFromIntent(context, result.data)) {
            is CloudBackup.Auth.Token -> scope.launch { withToken(auth.accessToken) }
            is CloudBackup.Auth.Failed -> { busy = null; message = auth.message }
            is CloudBackup.Auth.NeedsConsent -> { busy = null; message = "No se completó la conexión con Google" }
        }
    }

    fun connect() {
        scope.launch {
            busy = "Conectando…"
            message = null
            when (val auth = CloudBackup.authorize(context)) {
                is CloudBackup.Auth.Token -> withToken(auth.accessToken)
                is CloudBackup.Auth.NeedsConsent -> {
                    busy = null
                    launcher.launch(IntentSenderRequest.Builder(auth.intent).build())
                }
                is CloudBackup.Auth.Failed -> { busy = null; message = auth.message }
            }
        }
    }

    val account = email
    if (account == null) {
        Text(
            "Conecta tu cuenta de Google para no perder tus videos y canciones si cambias de teléfono. " +
                "Se guarda la lista (enlaces, categorías, notas y favoritos), no los archivos descargados.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { connect() }, enabled = busy == null, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Rounded.CloudSync, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Conectar con Google")
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudSync, null, tint = Mint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(account, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    if (last > 0) "Último respaldo: ${formatDate(last)}" else "Todavía sin respaldo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Se respalda solo cada vez que agregas, cambias o borras algo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (needsLogin) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Coral, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Google pide confirmar de nuevo. Toca «Conectar».", style = MaterialTheme.typography.bodySmall, color = Coral)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { connect() }, enabled = busy == null, shape = RoundedCornerShape(12.dp)) {
                Text(if (needsLogin) "Conectar" else "Respaldar ahora")
            }
            OutlinedButton(onClick = { confirmOff = true }, enabled = busy == null, shape = RoundedCornerShape(12.dp)) {
                Text("Desconectar")
            }
        }
    }

    busy?.let {
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Coral)
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
    message?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Coral, modifier = Modifier.padding(top = 10.dp))
    }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("¿Desconectar la cuenta?") },
            text = { Text("Se deja de respaldar en este teléfono. Lo que ya está en tu Drive no se borra.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmOff = false
                    message = null
                    scope.launch { CloudBackup.disconnect(context) }
                }) { Text("Desconectar", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Cancelar") } },
        )
    }
}

/** Ajustes → Acceso: lista numerada de teléfonos a los que se dio clave. */
@Composable
fun AccessContent(vm: MainViewModel) {
    val json by vm.prefs.grantedDevices.collectAsStateWithLifecycle()
    val devices = remember(json) { GrantedDevices.parse(json) }
    var grantOpen by remember { mutableStateOf(false) }
    var reopen by remember { mutableStateOf<GrantedDevice?>(null) }
    var removing by remember { mutableStateOf<GrantedDevice?>(null) }

    Text(
        "Para que otra persona use la app, pídele el código que le aparece al abrirla y genera aquí su clave. La clave solo sirve en ese celular.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = { grantOpen = true }, shape = RoundedCornerShape(12.dp)) { Text("Dar acceso a un celular") }

    Spacer(Modifier.height(12.dp))
    Text(
        if (devices.isEmpty()) "Todavía no has dado acceso a ningún teléfono"
        else "Teléfonos con acceso · ${devices.size}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    devices.forEachIndexed { i, d ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { reopen = d }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${i + 1}.",
                style = MaterialTheme.typography.titleMedium,
                color = Coral,
                modifier = Modifier.width(30.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(d.name.ifBlank { "Sin nombre" }, style = MaterialTheme.typography.bodyLarge)
                Text(
                    d.code + if (d.grantedAt > 0) " · " + formatDate(d.grantedAt) else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { removing = d }) { Icon(Icons.Rounded.DeleteOutline, "Quitar de la lista") }
        }
    }

    if (grantOpen) GrantAccessDialog(onDismiss = { grantOpen = false })
    reopen?.let { d -> GrantAccessDialog(onDismiss = { reopen = null }, initialCode = d.code, initialName = d.name) }
    removing?.let { d ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("¿Quitar de la lista?") },
            text = { Text("Solo se quita de esta lista. Si ese teléfono ya activó la app, la sigue pudiendo usar.") },
            confirmButton = {
                TextButton(onClick = { GrantedDevices.remove(vm.prefs, d.code); removing = null }) { Text("Quitar", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
        )
    }
}
