package com.project.bcv.core.network

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Server timestamps are local (Vietnam) wall-clock times without an offset, possibly with fractions. */
object ServerDates {
    fun parse(value: String?): Date? = value?.takeIf { it.isNotBlank() && it != "null" }?.let {
        runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(it.take(19)) }.getOrNull()
    }
}
