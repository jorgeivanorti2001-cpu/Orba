# Orba

Tus videos y tu música a tu manera, sin algoritmos, sin anuncios, siempre contigo.

Una sola app con las dos secciones: **Ver** (videos tipo reels, sin algoritmo) y **Escuchar** (música con cola, letra y segundo plano).

## Novedades 4.0

- Todo se llama Orba: nombre, ícono, logo y paquete (`com.orba.app`).
- Logo nuevo en mejor calidad, y el nombre ORBA con la letra del logo en el inicio.
- Agregar enlaces sin esperar: se reconocen solos mientras pegas, los repetidos se marcan en amarillo y se quitan con la X, y al tocar «Agregar» todo sigue en segundo plano. Al terminar llega un aviso.
- La carátula cambia siempre al pasar de canción con las flechas (también en la notificación).
- Los videos ya no se ven estirados: el cuadro se arma con la medida real de cada video.
- Carga más rápida: la app recuerda la forma de leer YouTube que funcionó y la usa primero; si hay que actualizar el motor, se hace sin hacer esperar el video.
- Subir volumen por video o canción (+3 a +12 dB): en Escuchar, ícono del parlante; en cualquier video o canción, en «Más» / los tres puntos. Se recuerda para la próxima vez.
- La onda alrededor de la carátula se mueve con el ritmo real de la canción.
- En Escuchar, los íconos de cada canción son más pequeños para que se lea más del nombre.
- Respaldo en Google Drive rehecho y con mensajes claros si algo falla.

## Compilar (GitHub)

1. En tu repositorio: **Add file → Upload files**, arrastra todo el contenido de esta carpeta (incluida la carpeta oculta `.github`) y **Commit changes**.
2. En **Actions** espera el ✓ verde y descarga **orba-apk**.
3. Descomprime e instala `Orba-arm64-v8a-release.apk`.

Como el paquete cambió a `com.orba.app`, Orba se instala como una app nueva. La versión anterior se puede desinstalar.

No borres `app/orba.jks`: es la firma de la app. Con ella las próximas versiones se instalan encima sin perder nada.

## Contraseña del dueño

Al abrir la app por primera vez aparece la pantalla de bloqueo. Escribe **Orba2026** en «Clave de acceso» y toca **Activar**. Así quedas como dueño y en Ajustes aparece **Acceso**, donde se generan las claves para otros teléfonos.

Se cambia en `app/build.gradle.kts`, línea `ADMIN_PASSWORD`.

## Respaldo con tu cuenta de Google (una sola vez)

En console.cloud.google.com, dentro del proyecto que ya creaste:

1. **APIs y servicios → Biblioteca → Google Drive API → Habilitar** (si ya está, no hay que hacer nada).
2. **Pantalla de consentimiento / Público**: modo prueba, con tu correo y el de tu hermano como usuarios de prueba.
3. **Credenciales → Crear credenciales → ID de cliente de OAuth → Android**:
   - Nombre del paquete: `com.orba.app`
   - SHA-1: `2A:67:D4:1B:14:7A:13:E9:65:1B:40:F6:93:4F:98:AD:7D:BC:4A:0D`
4. En la app: **Ajustes → Correo y respaldo → Conectar con Google**.

Si algo falla, la app muestra el motivo exacto (permiso de Drive sin marcar, API sin habilitar, cuenta que no está en la lista de prueba, etc.).

## Para que no se corte con la pantalla apagada (HyperOS / MIUI)

- Ajustes → Apps → Orba → **Ahorro de batería → Sin restricciones**.
- En Recientes, mantén presionada la tarjeta de Orba → ícono de **candado**.

## Si algo falla

- **"No se pudo leer el video"**: Ajustes → Motor de extracción → **Actualizar**.
- **Videos privados o que piden iniciar sesión** no se pueden leer.

## Cómo está hecha

| Parte | Tecnología | Archivo |
|---|---|---|
| Interfaz | Kotlin + Jetpack Compose | `ui/` |
| Reproductor y segundo plano | Media3 (ExoPlayer + MediaSessionService) | `playback/PlaybackService.kt` |
| Onda al ritmo de la música | Lectura del audio que sale (sin micrófono) | `playback/AudioLevels.kt` |
| Agregar en segundo plano | Cola propia con aviso al terminar | `ui/AddQueue.kt` |
| Lectura de enlaces y descargas | yt-dlp (youtubedl-android) + FFmpeg | `engine/Engine.kt`, `download/` |
| Base de datos | Room (SQLite) | `data/` |
| Respaldo | Google Drive (carpeta privada de la app) | `backup/CloudBackup.kt` |

## Uso responsable

Descargar de YouTube o Facebook va contra sus términos de servicio; la app está pensada para uso personal.
