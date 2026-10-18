package com.orba.app.ui

import com.orba.app.data.Video
import java.text.Normalizer

/** Texto sin tildes y en minúscula, para buscar sin importar cómo se escriba. */
fun String.searchable(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

/**
 * Coincide si cada palabra escrita aparece en cualquier parte del título o del artista.
 * Ej.: "keep" encuentra "A Violin Keep on Dancing"; "violin danc" también.
 */
fun Video.matchesQuery(query: String): Boolean {
    val words = query.searchable().split(' ', ',', '-').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val haystack = (title + " " + (uploader ?: "")).searchable()
    return words.all { it in haystack }
}

/** Orden alfabético de la A a la Z (ignora tildes, mayúsculas y signos al inicio). */
fun List<Video>.sortedAz(): List<Video> =
    sortedBy { v -> v.title.searchable().trimStart { !it.isLetterOrDigit() } }
