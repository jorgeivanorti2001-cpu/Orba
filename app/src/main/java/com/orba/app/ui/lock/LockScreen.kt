package com.orba.app.ui.lock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orba.app.R
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.BrandGradient
import com.orba.app.ui.theme.Rose
import com.orba.app.ui.theme.appBackground
import com.orba.app.ui.theme.glass

/** Pantalla que aparece si este teléfono no tiene permiso: no deja usar nada más. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val code = remember { License.deviceCode(context) }
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().appBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Image(painterResource(R.drawable.orba_emblem), null, Modifier.size(104.dp))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = Coral, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("App privada", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Para usarla en este teléfono necesitas una clave del dueño. Envíale este código:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(18.dp), strength = 1.5f, tint = Coral)
                    .clickable { copy(context, code) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(code, fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.ContentCopy, "Copiar", tint = Coral)
            }
            Text(
                "Toca para copiar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; error = null },
                label = { Text("Clave de acceso") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Coral, focusedLabelColor = Coral, cursorColor = Coral),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(BrandGradient))
                    .clickable {
                        // La misma casilla sirve para la clave que da el dueño y para la contraseña del dueño
                        if (License.activateAdmin(context, key) || License.activate(context, key)) onUnlocked()
                        else error = "Clave incorrecta para este teléfono"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Activar", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Ajustes → Acceso: el dueño escribe el código de otro teléfono y obtiene su clave. */
@Composable
fun GrantAccessDialog(onDismiss: () -> Unit, initialCode: String = "", initialName: String = "") {
    val context = LocalContext.current
    var code by remember { mutableStateOf(initialCode) }
    var name by remember { mutableStateOf(initialName) }
    val valid = License.isValidCode(code)
    val key = if (valid) License.keyFor(code) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dar acceso a otro teléfono") },
        text = {
            Column {
                Text(
                    "Escribe el código que aparece en la pantalla de bloqueo del otro teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Código del teléfono") },
                    placeholder = { Text("XXXX-XXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Coral, focusedLabelColor = Coral, cursorColor = Coral),
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("De quién es") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Coral, focusedLabelColor = Coral, cursorColor = Coral),
                )
                if (key != null) {
                    Spacer(Modifier.height(14.dp))
                    Text("Clave para ese teléfono:", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(14.dp), strength = 1.5f, tint = Coral)
                            .clickable { copy(context, key) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(key, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Rounded.ContentCopy, "Copiar", tint = Coral)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Queda en la lista numerada de Ajustes → Acceso
                if (valid) GrantedDevices.add(com.orba.app.data.Prefs.get(context), code, name.ifBlank { "Sin nombre" })
                onDismiss()
            }) { Text(if (valid) "Guardar en la lista" else "Cerrar", color = Coral) }
        },
    )
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Orba", text))
    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
}
