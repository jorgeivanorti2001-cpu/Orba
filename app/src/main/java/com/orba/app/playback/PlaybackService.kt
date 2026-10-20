package com.orba.app.playback

import android.app.PendingIntent
import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import android.os.Bundle
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.orba.app.data.Prefs
import com.orba.app.data.DownloadState
import com.orba.app.download.Downloads
import com.orba.app.data.Repository
import com.orba.app.data.isDownloaded
import com.orba.app.data.isAudio
import com.orba.app.engine.Engine
import com.orba.app.engine.StreamResolver
import com.orba.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.asListenableFuture
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Servicio que mantiene el reproductor vivo: sigue sonando con la pantalla apagada o
 * mientras usas otras apps, y muestra controles en la notificación y en la pantalla de bloqueo.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var repo: Repository
    private lateinit var prefs: Prefs
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Intentos de recuperación por video: 1 = volver a pedir la dirección, 2 = formato simple. */
    private val retries = mutableMapOf<Long, Int>()
    private var advanceJob: Job? = null
    private var pendingAdd: Job? = null

    /** Veces que se ha repetido el video actual (se reinicia al cambiar de video). */
    private var loops = 0

    /** Elementos que son audio (sección Escuchar): se comportan como música, con cola. */
    private val audioIds = mutableSetOf<Long>()
    private fun isAudio(id: Long?) = id != null && id in audioIds

    override fun onCreate() {
        super.onCreate()
        repo = Repository.get(this)
        prefs = Prefs.get(this)

        // Búfer moderado: arranca rápido y no gasta datos de más si pasas al siguiente video.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(12_000, 50_000, 600, 1_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // El audio pasa por un "espía" que mide volumen y graves para la onda de la carátula
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(TeeAudioProcessor(LevelsSink), booster))
                .build()
        }

        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(StreamMediaSourceFactory(this))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pausa si desconectas los audífonos
            .setWakeMode(C.WAKE_MODE_NETWORK)   // sigue cargando con la pantalla apagada
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(listener)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // La sesión usa un envoltorio que agrega "anterior / siguiente" para la música
        session = MediaSession.Builder(this, QueuePlayer(player))
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .setCustomLayout(ImmutableList.of(prevButton(), nextButton()))
            .build()

        // Botones "anterior / siguiente" de la app (mini reproductor y pantalla de reproducción)
        PlaybackControl.onNext = { goNext() }
        PlaybackControl.onPrevious = { goPrevious() }
        PlaybackControl.onBoost = { id, db -> scope.launch { if (currentId() == id) applyBoost(db) } }

        scope.launch { prefs.audioOnly.collect { applyAudioOnly(it) } }
        scope.launch { prefs.loop.collect { applyRepeat() } }
        scope.launch { prefs.audioRepeat.collect { applyRepeat() } }
        scope.launch { prefs.shuffle.collect { AudioQueue.reshuffle(currentId(), it) } }
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) savePosition()
            }
        }
    }

    /**
     * Bucle sin cortes: el reproductor repite solo el mismo video. Al llegar a
     * [Prefs.MAX_PLAYS] reproducciones se desactiva y el video se detiene al final.
     */
    private fun applyRepeat() {
        if (isAudio(currentId())) {
            player.repeatMode =
                if (prefs.audioRepeat.value == Prefs.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            return
        }
        val keepLooping = prefs.loop.value && loops < Prefs.MAX_PLAYS - 1
        player.repeatMode = if (keepLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // ---------- Refuerzo de volumen (por video o canción) ----------

    /** Sube el volumen dentro del propio reproductor (sirve igual para videos y canciones). */
    private val booster = BoostProcessor()

    private fun applyBoost(db: Int) {
        booster.targetDb = db.coerceIn(0, MAX_BOOST_DB)
    }

    private fun loadBoostFor(id: Long) {
        scope.launch {
            val db = repo.getVideo(id)?.boostDb ?: 0
            if (currentId() == id) applyBoost(db)
        }
    }

    private fun applyAudioOnly(audioOnly: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si cierras la app desde recientes y no está sonando nada, se apaga el servicio.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlaybackControl.onNext = null
        PlaybackControl.onPrevious = null
        PlaybackControl.onBoost = null
        savePosition()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------- Preparar videos ----------

    // ---------- Botones de la notificación ----------

    @Suppress("DEPRECATION")
    private fun prevButton(): CommandButton = CommandButton.Builder()
        .setDisplayName("Anterior")
        .setIconResId(android.R.drawable.ic_media_previous)
        .setSessionCommand(SessionCommand(CMD_PREV, Bundle.EMPTY))
        .build()

    @Suppress("DEPRECATION")
    private fun nextButton(): CommandButton = CommandButton.Builder()
        .setDisplayName("Siguiente")
        .setIconResId(android.R.drawable.ic_media_next)
        .setSessionCommand(SessionCommand(CMD_NEXT, Bundle.EMPTY))
        .build()

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_PREV, Bundle.EMPTY))
                .add(SessionCommand(CMD_NEXT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_NEXT -> goNext()
                CMD_PREV -> goPrevious()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            // Si pasas de video antes de que el anterior esté listo, ese pedido se abandona:
            // así el que estás viendo no hace fila detrás de los que ya pasaste.
            pendingAdd?.cancel()
            val work = scope.async {
                mediaItems.map { item ->
                    val id = item.mediaId.toLongOrNull() ?: error("Video no válido")
                    retries.remove(id)
                    resolve(id)
                }.toMutableList()
            }
            pendingAdd = work
            return work.asListenableFuture()
        }
    }

    /** Si el video está descargado se usa el archivo; si no, se buscan las direcciones en línea. */
    private suspend fun resolve(id: Long, fallback: Boolean = false): MediaItem {
        val video = repo.getVideo(id) ?: error("El video ya no existe")
        val artwork = video.localThumbPath?.let { Uri.fromFile(File(it)) }
            ?: video.thumbnailUrl?.let { Uri.parse(it) }
        // La carátula va también como imagen dentro de los datos: así la notificación y la
        // pantalla de bloqueo la cambian siempre al pasar de canción, sin depender de cargarla aparte.
        val artworkBytes = withContext(Dispatchers.IO) {
            video.localThumbPath?.let { File(it) }?.takeIf { it.exists() && it.length() in 1..1_500_000 }
                ?.let { runCatching { it.readBytes() }.getOrNull() }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(video.title)
            .setArtist(video.uploader ?: Engine.platformLabel(video.platform))
            .setArtworkUri(artwork)
            .apply { if (artworkBytes != null) setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
            .build()
        val builder = MediaItem.Builder().setMediaId(id.toString()).setMediaMetadata(metadata)
        if (video.isAudio()) audioIds.add(id) else audioIds.remove(id)

        if (video.isDownloaded()) {
            return builder.setUri(Uri.fromFile(File(video.localFilePath!!))).build()
        }

        val maxHeight = prefs.maxHeight.value
        val streams = try {
            val height = if (video.isAudio()) Engine.audioHeight(prefs.audioQuality.value) else maxHeight
            if (fallback) Engine.resolveStreams(this, video.sourceUrl, height, fallback = true)
            else StreamResolver.get(this, video.sourceUrl, height)
        } catch (t: Throwable) {
            if (Engine.looksRemoved(t)) repo.setUnavailable(id, true)
            PlaybackEvents.message(
                if (Engine.looksRemoved(t)) "Este video fue eliminado o bloqueado y no lo alcanzaste a descargar"
                else Engine.friendlyError(t)
            )
            throw t
        }
        if (video.unavailableOnline) repo.setUnavailable(id, false)
        return builder
            .setUri(streams.videoUrl)
            .setTag(StreamTag(streams.audioUrl, streams.headers))
            .build()
    }

    private fun currentId(): Long? = player.currentMediaItem?.mediaId?.toLongOrNull()

    private fun savePosition(atZero: Boolean = false) {
        val id = currentId() ?: return
        val pos = if (atZero) 0L else player.currentPosition
        scope.launch(Dispatchers.IO) { repo.savePosition(id, pos) }
    }

    private fun playId(id: Long, startMs: Long, fallback: Boolean = false) {
        advanceJob?.cancel()
        advanceJob = scope.launch {
            try {
                val item = resolve(id, fallback)
                player.setMediaItem(item, startMs)
                player.prepare()
                player.play()
            } catch (_: Throwable) {
                // el mensaje ya se mostró en resolve()
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state != Player.STATE_ENDED) return
            savePosition(atZero = true)
            // Música: al terminar una canción pasa a la siguiente de la cola
            val id = currentId()
            if (isAudio(id)) {
                AudioQueue.next(id!!, wrap = prefs.audioRepeat.value == Prefs.REPEAT_ALL)?.let { playId(it, 0) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                loops++
            } else {
                loops = 0
                mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    scope.launch(Dispatchers.IO) { repo.markPlayed(id) }
                    loadBoostFor(id)
                }
            }
            applyRepeat()
        }


        /** El tamaño real del video corrige si es vertical u horizontal. */
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
            val id = currentId() ?: return
            val w = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
            val h = videoSize.height
            scope.launch(Dispatchers.IO) {
                val v = repo.getVideo(id) ?: return@launch
                if (v.isVertical != (h > w) || v.width != w || v.height != h) repo.setDimensions(id, w, h)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) savePosition()
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = currentId() ?: return
            val isLocal = player.currentMediaItem?.localConfiguration?.uri?.scheme == "file"
            val attempt = (retries[id] ?: 0) + 1
            retries[id] = attempt
            if (isLocal) {
                PlaybackEvents.message("No se pudo reproducir este video")
                return
            }
            if (attempt > 2) {
                // Algunas plataformas (TikTok, Instagram) no dejan ver en línea desde fuera de su app:
                // en ese caso se descarga y se ve desde el teléfono.
                scope.launch {
                    val v = repo.getVideo(id)
                    if (v != null && (v.downloadState == DownloadState.NONE || v.downloadState == DownloadState.FAILED)) {
                        Downloads.enqueue(this@PlaybackService, id)
                    }
                    PlaybackEvents.message("No se pudo ver en línea. Se está descargando: en un momento lo puedes ver")
                }
                return
            }
            val position = player.currentPosition
            scope.launch {
                repo.getVideo(id)?.let { StreamResolver.invalidate(it.sourceUrl) }
                // 1er intento: las direcciones caducaron. 2do: formato único más compatible.
                playId(id, position, fallback = attempt == 2)
            }
        }
    }

    // ---------- Anterior / siguiente ----------

    private fun goNext() {
        val id = currentId() ?: return
        if (!isAudio(id)) { player.seekForward(); return }
        AudioQueue.next(id, wrap = true)?.let { playId(it, 0) }
    }

    private fun goPrevious() {
        val id = currentId() ?: return
        if (!isAudio(id)) { player.seekBack(); return }
        // Como en cualquier reproductor de música: si ya avanzó, vuelve al inicio de la canción
        if (player.currentPosition > 3_000) { player.seekTo(0); return }
        val prev = AudioQueue.previous(id, wrap = true)
        if (prev != null) playId(prev, 0) else player.seekTo(0)
    }

    private inner class QueuePlayer(base: Player) : ForwardingPlayer(base) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

        override fun isCommandAvailable(command: Int): Boolean = when (command) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }

        override fun hasNextMediaItem(): Boolean = isAudio(currentId()) || super.hasNextMediaItem()
        override fun hasPreviousMediaItem(): Boolean = isAudio(currentId()) || super.hasPreviousMediaItem()
        override fun seekToNext() = goNext()
        override fun seekToNextMediaItem() = goNext()
        override fun seekToPrevious() = goPrevious()
        override fun seekToPreviousMediaItem() = goPrevious()
    }

    /** Recibe el audio que sale (sin modificarlo) y se lo pasa a [AudioLevels]. */
    private object LevelsSink : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            AudioLevels.onFlush(
                sampleRateHz,
                channelCount,
                isFloat = encoding == C.ENCODING_PCM_FLOAT,
                isPcm16 = encoding == C.ENCODING_PCM_16BIT,
            )
        }

        override fun handleBuffer(buffer: ByteBuffer) = AudioLevels.onBuffer(buffer)
    }

    companion object {
        const val MAX_BOOST_DB = 20
        private const val CMD_NEXT = "orba.NEXT"
        private const val CMD_PREV = "orba.PREV"
    }
}
