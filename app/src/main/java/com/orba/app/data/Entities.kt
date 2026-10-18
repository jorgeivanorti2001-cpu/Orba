package com.orba.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

object DownloadState {
    const val NONE = 0
    const val QUEUED = 1
    const val DOWNLOADING = 2
    const val DONE = 3
    const val FAILED = 4
}

/** Tipo de contenido: video (sección Ver) o audio (sección Escuchar). */
object Kind {
    const val VIDEO = 0
    const val AUDIO = 1
}

/** Cómo se muestra un video: lo que detecta la app, o lo que el usuario fuerza. */
object Orientation {
    const val AUTO = 0
    const val VERTICAL = 1
    const val HORIZONTAL = 2
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val kind: Int = Kind.VIDEO,
)

@Entity(
    tableName = "videos",
    // Un mismo enlace puede estar una vez como video y otra como audio
    indices = [Index(value = ["sourceUrl", "kind"], unique = true), Index("categoryId")],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
)
data class Video(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val platform: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val localThumbPath: String? = null,
    val durationSec: Int,
    val width: Int,
    val height: Int,
    val isVertical: Boolean,
    val categoryId: Long?,
    val addedAt: Long = System.currentTimeMillis(),
    val localFilePath: String? = null,
    val fileSizeBytes: Long = 0,
    val downloadState: Int = DownloadState.NONE,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null,
    val lastPositionMs: Long = 0,
    val unavailableOnline: Boolean = false,
    @ColumnInfo(defaultValue = "0") val orientation: Int = Orientation.AUTO,
    @ColumnInfo(defaultValue = "0") val lastPlayedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val kind: Int = Kind.VIDEO,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** Refuerzo de volumen propio de este video o canción, en decibeles (0 = normal). */
    @ColumnInfo(defaultValue = "0") val boostDb: Int = 0,
)

fun Video.isAudio(): Boolean = kind == Kind.AUDIO

/** Audio agregado desde la memoria del teléfono (marca amarilla). */
fun Video.isLocal(): Boolean = platform == "local"

fun Video.isDownloaded(): Boolean =
    downloadState == DownloadState.DONE && localFilePath != null && File(localFilePath).exists()

/**
 * Vista del video: todos se ven en vertical (tipo reel) salvo que el usuario elija
 * la vista horizontal con el botón. Así el scroll no cambia de diseño entre un video y otro.
 */
fun Video.showsVertical(): Boolean = orientation != Orientation.HORIZONTAL

/** Miniatura guardada en el teléfono si existe, si no la de internet. */
fun Video.thumbModel(): Any? = localThumbPath?.let { File(it) }?.takeIf { it.exists() } ?: thumbnailUrl

@Entity(
    tableName = "notes",
    indices = [Index("videoId")],
    foreignKeys = [
        ForeignKey(
            entity = Video::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: Long,
    val text: String,
    /** Minuto del video al que se refiere la nota (null = nota general). */
    val positionMs: Long?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class NoteCount(val videoId: Long, val count: Int)
