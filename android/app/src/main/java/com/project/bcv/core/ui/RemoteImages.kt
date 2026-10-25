package com.project.bcv.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Small HTTPS image loader with an in-memory cache shared across screens. */
object RemoteImages {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(url: String, sample: Int): Bitmap? = cache.get("$sample|$url")

    /** Decodes [url] at 1/[sample] size; returns null when it cannot be fetched. */
    suspend fun load(url: String, sample: Int): Bitmap? {
        if (!url.startsWith("https://")) return null
        cached(url, sample)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("User-Agent", "BCV Android App (educational demo)")
                try {
                    connection.inputStream.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                } finally { connection.disconnect() }
            } catch (_: Exception) { null }
        }
        bitmap?.let { cache.put("$sample|$url", it) }
        return bitmap
    }
}
