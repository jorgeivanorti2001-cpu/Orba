package com.orba.app.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.orba.app.OrbaApp
import com.orba.app.R
import com.orba.app.data.DuplicateVideoException
import com.orba.app.data.Kind
import com.orba.app.data.Prefs
import com.orba.app.data.Repository
import com.orba.app.download.Downloads
import com.orba.app.engine.Engine
import com.orba.app.playback.PlaybackEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class LinkStatus {
    /** Leyendo título y miniatura. */
    CHECKING,
    /** Listo para agregar. */
    READY,
    /** Ya está en tu lista (se puede quitar con la X). */
    DUPLICATE,
    /** No se pudo leer. */
    ERROR,
}

/** Un enlace en la pantalla de "Agregar", con lo que se sabe de él. */
data class PendingLink(
    val url: String,
    val status: LinkStatus = LinkStatus.CHECKING,
    val title: String? = null,
    val message: String? = null,
    val existingId: Long? = null,
)

/** Si la app está a la vista (para decidir si el aviso va en la app o en la barra de notificaciones). */
object AppVisibility {
    @Volatile
    var foreground: Boolean = false
}

/**
 * Agregar enlaces sin esperar:
 *  - Cada enlace que pegas se empieza a reconocer enseguida (título, miniatura y si ya está en tu lista).
 *  - Al tocar «Agregar», la pantalla se cierra y todo sigue en segundo plano, aunque no hayan terminado de reconocerse.
 *  - Al terminar llega un aviso con cuántos se agregaron.
 */
object AddQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Máximo dos lecturas a la vez: más rápido sin poner lento el teléfono. */
    private val gate = Semaphore(2)

    private val _drafts = MutableStateFlow<List<PendingLink>>(emptyList())
    /** Enlaces en la pantalla de agregar, todavía sin confirmar. */
    val drafts: StateFlow<List<PendingLink>> = _drafts.asStateFlow()

    private val _working = MutableStateFlow(0)
    /** Cuántos se están agregando ahora mismo en segundo plano. */
    val working: StateFlow<Int> = _working.asStateFlow()

    /** La lectura de cada enlace se hace una sola vez y se reutiliza al agregarlo. */
    private val reads = ConcurrentHashMap<String, Deferred<Engine.Metadata>>()

    /** Enlaces que ya pasaron a segundo plano (su lectura no se cancela al quitarlos de la pantalla). */
    private val submitted = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var checkedKind = Kind.VIDEO

    /** Último texto tomado del portapapeles (para no volver a pegarlo solo cada vez que se abre Agregar). */
    @Volatile
    var lastClip: String? = null

    private fun read(app: Context, url: String, kind: Int): Deferred<Engine.Metadata> =
        reads.computeIfAbsent(url) {
            scope.async { gate.withPermit { Engine.fetchMetadata(app, url, audio = kind == Kind.AUDIO) } }
        }

    private fun set(url: String, change: (PendingLink) -> PendingLink) =
        _drafts.update { list -> list.map { if (it.url == url) change(it) else it } }

    /** Toma todos los enlaces del texto y los suma (sin repetir). Devuelve cuántos entraron. */
    fun add(context: Context, text: String, kind: Int): Int {
        val app = context.applicationContext
        val urls = Engine.extractUrls(text)
        var fresh: List<String> = emptyList()
        _drafts.update { current ->
            val existing = current.map { it.url }.toSet()
            fresh = urls.filter { it !in existing }
            current + fresh.map { PendingLink(it) }
        }
        checkedKind = kind
        fresh.forEach { check(app, it, kind) }
        return fresh.size
    }

    /** Al abrir la pantalla para la otra sección (Ver / Escuchar), se revisa de nuevo qué está repetido. */
    fun setKind(context: Context, kind: Int) {
        if (kind == checkedKind) return
        checkedKind = kind
        val app = context.applicationContext
        _drafts.value.forEach { check(app, it.url, kind) }
    }

    fun remove(url: String) {
        _drafts.update { list -> list.filterNot { it.url == url } }
        if (url !in submitted) reads.remove(url)?.cancel()
    }

    private fun check(app: Context, url: String, kind: Int) = scope.launch {
        val repo = Repository.get(app)
        repo.findDuplicate(url, kind)?.let { dup ->
            set(url) { it.copy(status = LinkStatus.DUPLICATE, title = dup.title, existingId = dup.id, message = "Ya está en tu lista") }
            return@launch
        }
        set(url) { it.copy(status = LinkStatus.CHECKING, message = null) }
        try {
            val m = read(app, url, kind).await()
            val dup = m.canonicalUrl?.takeIf { it != url }?.let { repo.findDuplicate(it, kind) }
            set(url) {
                if (dup != null) it.copy(status = LinkStatus.DUPLICATE, title = dup.title, existingId = dup.id, message = "Ya está en tu lista")
                else it.copy(status = LinkStatus.READY, title = m.title, message = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            reads.remove(url)
            set(url) { it.copy(status = LinkStatus.ERROR, message = Engine.friendlyError(t)) }
        }
    }

    /**
     * Pasa a segundo plano todo lo que no esté repetido. Devuelve cuántos se van a agregar.
     * Lo que falle vuelve a la pantalla de agregar, para que lo veas y decidas.
     */
    fun submit(context: Context, categoryId: Long?, kind: Int): Int {
        val app = context.applicationContext
        val batch = _drafts.value.filter { it.status != LinkStatus.DUPLICATE }
        _drafts.value = emptyList()
        if (batch.isEmpty()) return 0
        batch.forEach { submitted.add(it.url) }
        _working.update { it + batch.size }

        scope.launch {
            val repo = Repository.get(app)
            val prefs = Prefs.get(app)
            val added = AtomicInteger(0)
            val repeated = AtomicInteger(0)
            val failed = Collections.synchronizedList(mutableListOf<PendingLink>())
            batch.map { link ->
                async {
                    try {
                        val m = read(app, link.url, kind).await()
                        val id = repo.insertFromMetadata(link.url, m, categoryId, kind)
                        added.incrementAndGet()
                        val auto = if (kind == Kind.AUDIO) prefs.autoDownloadAudio.value else prefs.autoDownload.value
                        if (auto) runCatching { Downloads.enqueue(app, id) }
                    } catch (e: DuplicateVideoException) {
                        repeated.incrementAndGet()
                    } catch (e: SQLiteConstraintException) {
                        repeated.incrementAndGet()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        failed += link.copy(status = LinkStatus.ERROR, message = Engine.friendlyError(t))
                    } finally {
                        reads.remove(link.url)
                        submitted.remove(link.url)
                        _working.update { (it - 1).coerceAtLeast(0) }
                    }
                }
            }.awaitAll()
            if (failed.isNotEmpty()) {
                _drafts.update { current -> current + failed.filter { f -> current.none { it.url == f.url } } }
            }
            announce(app, kind, added.get(), repeated.get(), failed.size)
        }
        return batch.size
    }

    // ---------- Aviso al terminar ----------

    private fun announce(app: Context, kind: Int, added: Int, repeated: Int, failed: Int) {
        val audio = kind == Kind.AUDIO
        val main = when {
            added == 1 -> if (audio) "Se agregó 1 canción" else "Se agregó 1 video"
            added > 1 -> if (audio) "Se agregaron $added canciones" else "Se agregaron $added videos"
            failed > 0 -> "No se pudo agregar"
            else -> if (audio) "Esas canciones ya estaban en tu lista" else "Esos videos ya estaban en tu lista"
        }
        val extra = buildList {
            if (repeated > 0 && added > 0) add(if (repeated == 1) "1 ya estaba" else "$repeated ya estaban")
            if (failed > 0) add(if (failed == 1) "1 enlace falló (está en Agregar)" else "$failed enlaces fallaron (están en Agregar)")
        }
        val text = (listOf(main) + extra).joinToString(" · ")
        PlaybackEvents.message(text)
        if (!AppVisibility.foreground) notify(app, text)
    }

    private fun notify(app: Context, text: String) {
        runCatching {
            val nm = NotificationManagerCompat.from(app)
            if (!nm.areNotificationsEnabled()) return
            val open = PendingIntent.getActivity(
                app, 7,
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(app, OrbaApp.CHANNEL_NOTICES)
                .setSmallIcon(R.drawable.ic_stat_orba)
                .setContentTitle("Orba")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(4242, n)
        }
    }
}
