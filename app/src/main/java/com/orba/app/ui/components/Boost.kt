package com.orba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orba.app.ui.theme.Coral

/** Pasos del refuerzo de volumen, en decibeles. */
val BoostSteps = listOf(0, 3, 6, 9, 12)

fun boostLabel(db: Int): String = if (db <= 0) "Normal" else "+$db dB"

/**
 * Refuerzo de volumen para un video o canción que se oye bajito. Se guarda para ese elemento:
 * la próxima vez que suene, ya sale con el volumen que elegiste.
 */
@Composable
fun BoostPicker(current: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = if (current > 0) Coral else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Subir volumen", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                if (current > 0) "Solo para este" else "Por si se oye bajito",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BoostSteps.forEach { db ->
                val selected = db == current
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Coral.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.07f))
                        .clickable { onPick(db) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (db == 0) "Normal" else "+$db",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
