package com.orba.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.orba.app.R
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput

// ---------------- Marca Orba ----------------
val OrbaBlue = Color(0xFF3B82F6)
val OrbaViolet = Color(0xFF8B5CF6)
val OrbaMagenta = Color(0xFFEC4899)
val OrbaCyan = Color(0xFF22D3EE)
val Carbon = Color(0xFF0B0F1A)
val ColdGray = Color(0xFF1F2937)

/** Degradado de la marca (azul → violeta → magenta), como el anillo del logo. */
val BrandGradient = listOf(OrbaBlue, OrbaViolet, OrbaMagenta)

/** Acento para íconos y textos destacados: violeta claro, se lee bien sobre el fondo oscuro. */
val Coral = Color(0xFFA78BFA)
val Rose = OrbaMagenta
val Mint = Color(0xFF4FC3A1)
val Violet = OrbaViolet
val Ink = Carbon
val Panel = Color(0xFF111827)
val PanelHigh = ColdGray
val Muted = Color(0xFF9CA3AF)

/** Color de las hojas y ventanas: casi opaco, deja ver un poco lo de atrás. */
val SheetColor = Color(0xF2111827)

private val colors = darkColorScheme(
    primary = OrbaViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E1F5E),
    onPrimaryContainer = Color(0xFFE4DBFF),
    secondary = OrbaCyan,
    onSecondary = Color(0xFF032A31),
    secondaryContainer = Color(0xFF0E3A44),
    onSecondaryContainer = Color(0xFFC9F6FD),
    tertiary = OrbaMagenta,
    background = Ink,
    onBackground = Color(0xFFEDEFF5),
    surface = Ink,
    onSurface = Color(0xFFEDEFF5),
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = Color(0xFF283344),
    surfaceContainerLow = Color(0xFF0E1320),
    outline = Color(0xFF374151),
    outlineVariant = Color(0xFF263040),
    error = Color(0xFFFF6B8B),
)

/** Tipografía de la marca: Manrope. */
val Manrope = FontFamily(
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val typography = Typography().run {
    fun TextStyle.m() = copy(fontFamily = Manrope)
    copy(
        displayLarge = displayLarge.m(),
        displayMedium = displayMedium.m(),
        displaySmall = displaySmall.m().copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineLarge = headlineLarge.m(),
        headlineMedium = headlineMedium.m().copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.m(),
        titleLarge = titleLarge.m().copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.m().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.m().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.m(),
        bodyMedium = bodyMedium.m(),
        bodySmall = bodySmall.m().merge(TextStyle(lineHeight = 17.sp)),
        labelLarge = labelLarge.m().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.m().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.m(),
    )
}

@Composable
fun OrbaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Todo lo que no tenga un color propio (íconos, textos) sale en blanco: nada negro sobre fondo oscuro
        CompositionLocalProvider(LocalContentColor provides Color(0xFFEDEFF5), content = content)
    }
}

// ---------------- Efecto vidrio (translúcido) ----------------

/** Fondo de la app: oscuro con resplandores de color difusos. */
fun Modifier.appBackground(): Modifier = this
    .background(Ink)
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(OrbaBlue.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.02f),
                radius = size.width * 0.95f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(OrbaViolet.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 1.0f, size.height * 0.45f),
                radius = size.width * 0.9f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(OrbaMagenta.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.95f),
                radius = size.width * 0.9f,
            )
        )
    }

/**
 * Superficie de vidrio: relleno blanco muy suave y un borde que brilla arriba.
 * [strength] sube o baja la opacidad (más alto = más sólido).
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(20.dp),
    strength: Float = 1f,
    tint: Color = Color.White,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(tint.copy(alpha = 0.10f * strength), tint.copy(alpha = 0.04f * strength))
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.03f))),
        shape,
    )

/** Vidrio oscuro para poner encima de videos (botones flotantes, barra inferior). */
fun Modifier.darkGlass(shape: Shape = RoundedCornerShape(20.dp), alpha: Float = 0.45f): Modifier = this
    .clip(shape)
    .background(Color(0xFF070A12).copy(alpha = alpha))
    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)

val NavBarHeight: Dp = 64.dp

/** Espacio que deja el contenido para que la barra flotante no lo tape. */
val NavClearance: Dp = NavBarHeight + 24.dp

/**
 * Para pantallas superpuestas: al ser un área "tocable", los toques no atraviesan
 * hacia lo que hay debajo (el feed), y los botones de la propia pantalla funcionan normal.
 */
fun Modifier.blockTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}
