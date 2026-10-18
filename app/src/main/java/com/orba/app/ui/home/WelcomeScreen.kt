package com.orba.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orba.app.R
import com.orba.app.BuildConfig
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.BrandGradient
import com.orba.app.ui.theme.Rose
import com.orba.app.ui.theme.appBackground
import com.orba.app.ui.theme.blockTouches
import com.orba.app.ui.theme.glass

/** Bienvenida: se muestra una sola vez para saber cómo saludar al usuario. */
@Composable
fun WelcomeScreen(onDone: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val finish = { onDone(name.trim()) }

    Box(Modifier.fillMaxSize().appBackground().blockTouches()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.orba_emblem), null, Modifier.size(132.dp))
            Spacer(Modifier.height(14.dp))
            Image(painterResource(R.drawable.orba_wordmark), "Orba", Modifier.height(22.dp).aspectRatio(6.65f))
            Spacer(Modifier.height(22.dp))
            Text(
                "Bienvenido a " + stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                (when {
                    BuildConfig.MUSIC_ONLY -> "Tu música, sin algoritmos."
                    BuildConfig.ALL_IN_ONE -> "Tus videos y tu música a tu manera, sin algoritmos."
                    else -> "Tus videos, sin algoritmos."
                }) + " ¿Cómo te llamas?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            TextField(
                value = name,
                onValueChange = { if (it.length <= 24) name = it },
                modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), strength = 1.4f),
                placeholder = { Text("Tu nombre") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) finish() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.height(16.dp))
            val enabled = name.isNotBlank()
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (enabled) Brush.horizontalGradient(BrandGradient)
                        else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
                    )
                    .clickable(enabled = enabled) { finish() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Empezar",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onDone("") }, modifier = Modifier.padding(top = 6.dp)) {
                Text("Ahora no", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
