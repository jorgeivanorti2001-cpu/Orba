package com.orba.app.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.orba.app.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

const val DEFAULT_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/**
 * Caché en disco de lo que se reproduce en línea. Sirve para dos cosas:
 * que el bucle no vuelva a gastar datos, y precargar el comienzo del siguiente video
 * para que al deslizar empiece al instante, como en Reels o Shorts.
 */
@UnstableApi
object MediaCache {
    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, "media"),
                LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }

    fun httpFactory(headers: Map<String, String>): DefaultHttpDataSource.Factory {
        val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
        return DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setUserAgent(ua ?: DEFAULT_UA)
            .setDefaultRequestProperties(headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })
    }
}

/** Descarga en segundo plano los primeros segundos del próximo video. */
@UnstableApi
object Preloader {
    private const val VIDEO_BYTES = 2L * 1024 * 1024
    private const val AUDIO_BYTES = 400L * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val writers = mutableListOf<CacheWriter>()
    private var lastKey: String? = null

    @Synchronized
    fun warm(context: Context, streams: Engine.Streams) {
        if (lastKey == streams.videoUrl) return
        lastKey = streams.videoUrl
        cancelAll()
        val app = context.applicationContext
        job = scope.launch {
            warmOne(app, streams.videoUrl, streams.headers, VIDEO_BYTES)
            streams.audioUrl?.let { warmOne(app, it, streams.headers, AUDIO_BYTES) }
        }
    }

    @Synchronized
    private fun cancelAll() {
        job?.cancel()
        writers.forEach { runCatching { it.cancel() } }
        writers.clear()
    }

    private fun warmOne(context: Context, url: String, headers: Map<String, String>, bytes: Long) {
        val dataSource = CacheDataSource.Factory()
            .setCache(MediaCache.get(context))
            .setUpstreamDataSourceFactory(MediaCache.httpFactory(headers))
            .createDataSource()
        val spec = DataSpec.Builder().setUri(url).setPosition(0).setLength(bytes).build()
        val writer = CacheWriter(dataSource, spec, null, null)
        synchronized(this) { writers.add(writer) }
        runCatching { writer.cache() }
        synchronized(this) { writers.remove(writer) }
    }
}
