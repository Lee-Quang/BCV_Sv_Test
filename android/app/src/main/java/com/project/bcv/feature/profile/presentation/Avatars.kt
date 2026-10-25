package com.project.bcv.feature.profile.presentation

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.project.bcv.core.ui.RemoteImages
import java.io.ByteArrayOutputStream

/**
 * Avatars are uploaded as small square JPEG data URIs. The server stores them in Cloudflare R2 and
 * answers with the image URL; without R2 it keeps the data URI, so both forms can come back.
 */
object Avatars {
    private const val PREFIX = "data:image/jpeg;base64,"
    private const val SIZE = 256

    /** The avatar if it is inline or already cached; a URL not fetched yet gives null (use [load]). */
    fun immediate(value: String?): Bitmap? =
        if (value?.startsWith("https://") == true) RemoteImages.cached(value, 1) else decode(value)

    suspend fun load(value: String?): Bitmap? =
        if (value?.startsWith("https://") == true) RemoteImages.load(value, 1) else decode(value)

    private fun decode(dataUri: String?): Bitmap? = dataUri?.takeIf { it.startsWith(PREFIX) }?.let {
        runCatching {
            val bytes = Base64.decode(it.substring(PREFIX.length), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    /** Centre-crops the picked image to a square, scales it to 256 px and encodes it. */
    fun encode(resolver: ContentResolver, uri: Uri): String {
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also applies the photo's EXIF rotation.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val shortest = minOf(info.size.width, info.size.height)
                if (shortest > SIZE * 2) decoder.setTargetSampleSize(shortest / (SIZE * 2))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val shortest = minOf(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = (shortest / (SIZE * 2)).coerceAtLeast(1) }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IllegalArgumentException("Unreadable image")
        }
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return PREFIX + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun initials(name: String): String = name.split(' ').filter(String::isNotBlank)
        .let { words -> if (words.size >= 2) "${words.first().first()}${words.last().first()}" else name.take(2) }
        .uppercase()
}
