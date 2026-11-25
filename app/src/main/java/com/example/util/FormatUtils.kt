package com.example.util

import java.util.Locale

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeedMbps(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0.0 MB/s"
        val mbPerSec = bytesPerSec / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB/s", mbPerSec)
    }

    fun formatDurationMmSs(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        val mins = safeSeconds / 60L
        val secs = safeSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun formatRelativeTimeAr(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return ""
        val diffMillis = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
        val minutes = diffMillis / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            minutes < 2L -> "الآن"
            minutes < 60L -> "منذ $minutes دقيقة"
            hours < 24L -> "منذ $hours ساعة"
            days == 1L -> "أمس"
            else -> "منذ $days أيام"
        }
    }
}
