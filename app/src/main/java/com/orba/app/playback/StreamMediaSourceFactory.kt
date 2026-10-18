package com.orba.app.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/** Datos extra que acompañan a un video en línea: audio separado y cabeceras HTTP. */
data class StreamTag(val audioUrl: String?, val headers: Map<String, String>)

/**
 * YouTube entrega el video y el audio en archivos distintos para calidades altas.
 * Esta fábrica los une en tiempo real; si el audio no viene aparte, reproduce normal.
 * Todo lo que llega de internet pasa por la caché (ver [MediaCache]).
 * En modo "solo audio" el reproductor ignora la pista de video y no la descarga.
 */
@UnstableApi
class StreamMediaSourceFactory(private val context: Context) : MediaSource.Factory {
    private var drmProvider: DrmSessionManagerProvider? = null
    private var errorPolicy: LoadErrorHandlingPolicy? = null

    private val localFactory = DefaultMediaSourceFactory(context)

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        drmProvider = provider
        localFactory.setDrmSessionManagerProvider(provider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        errorPolicy = policy
        localFactory.setLoadErrorHandlingPolicy(policy)
        return this
    }

    override fun getSupportedTypes(): IntArray = localFactory.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val tag = mediaItem.localConfiguration?.tag as? StreamTag
            ?: return localFactory.createMediaSource(mediaItem)

        val cached = CacheDataSource.Factory()
            .setCache(MediaCache.get(context))
            .setUpstreamDataSourceFactory(MediaCache.httpFactory(tag.headers))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val progressive = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context, cached))
        drmProvider?.let { progressive.setDrmSessionManagerProvider(it) }
        errorPolicy?.let { progressive.setLoadErrorHandlingPolicy(it) }

        val video = progressive.createMediaSource(mediaItem)
        val audioUrl = tag.audioUrl ?: return video
        val audio = progressive.createMediaSource(MediaItem.fromUri(audioUrl))
        return MergingMediaSource(true, video, audio)
    }
}
