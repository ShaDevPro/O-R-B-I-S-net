package com.sha.orbis.ui.social

import android.content.Context
import com.sha.orbis.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formateur d'horodatage relatif intelligent façon Facebook / Instagram.
 *
 * Règles :
 * - < 1 minute : "À l'instant" / "Just now" / "الآن"
 * - < 60 minutes : "X min" / "Xm ago" / "منذ X د"
 * - < 24 heures (même jour) : "X h" / "Xh ago" / "منذ X س"
 * - Hier : "Hier à HH:mm" / "Yesterday at HH:mm" / "أمس في HH:mm"
 * - Même année : "d MMM à HH:mm" / "d MMM at HH:mm" / "d MMM في HH:mm"
 * - Année différente : "d MMM yyyy"
 */
object SocialDateFormatter {

    fun formatRelative(context: Context, timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        // Si horodatage futur ou dans la dernière minute
        if (diff < 60_000L) {
            return context.getString(R.string.time_just_now)
        }

        val postCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }

        val isSameYear = postCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
        val isSameDay = isSameYear && postCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) {
            if (diff < 3_600_000L) {
                val minutes = (diff / 60_000L).coerceAtLeast(1)
                return context.getString(R.string.time_minutes_ago, minutes)
            }
            val hours = (diff / 3_600_000L).coerceAtLeast(1)
            return context.getString(R.string.time_hours_ago, hours)
        }

        // Vérifier si hier
        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = yesterdayCal.get(Calendar.YEAR) == postCal.get(Calendar.YEAR) &&
                yesterdayCal.get(Calendar.DAY_OF_YEAR) == postCal.get(Calendar.DAY_OF_YEAR)

        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeString = timeFormatter.format(Date(timestamp))

        if (isYesterday) {
            return context.getString(R.string.time_yesterday_at, timeString)
        }

        return if (isSameYear) {
            val dateFormatter = SimpleDateFormat("d MMM", Locale.getDefault())
            val dateString = dateFormatter.format(Date(timestamp))
            context.getString(R.string.time_at, dateString, timeString)
        } else {
            val fullDateFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            fullDateFormatter.format(Date(timestamp))
        }
    }

    fun formatCompact(context: Context, timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        if (diff < 60_000L) {
            return context.getString(R.string.time_just_now)
        }
        if (diff < 3_600_000L) {
            val minutes = (diff / 60_000L).coerceAtLeast(1)
            return context.getString(R.string.time_minutes_ago, minutes)
        }
        if (diff < 86_400_000L) {
            val hours = (diff / 3_600_000L).coerceAtLeast(1)
            return context.getString(R.string.time_hours_ago, hours)
        }

        val postCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val isSameYear = postCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)

        return if (isSameYear) {
            SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
        } else {
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
