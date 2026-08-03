package it.dogior.hadEnough.util

import java.time.LocalDate
import java.time.Year
import java.util.Locale

internal data class StreamingCommunityAvailability(
    val hasDate: Boolean,
    val date: String? = null,
    val precision: Precision = Precision.UNKNOWN,
    val isFuture: Boolean = false,
) {
    enum class Precision { DAY, YEAR, UNKNOWN }
}

internal object StreamingCommunityAvailabilityResolver {
    private val upcomingTokens = setOf("upcoming", "inproduction", "postproduction", "planned", "announced", "inarrivo", "comingsoon")
    private val releasedTokens = setOf("released", "returningseries", "ended", "cancelled", "canceled")

    fun parseDate(value: String?): StreamingCommunityAvailability {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return StreamingCommunityAvailability(false)
        runCatching { LocalDate.parse(text.take(10)) }.getOrNull()?.let { date ->
            return StreamingCommunityAvailability(true, text.take(10), StreamingCommunityAvailability.Precision.DAY, date.isAfter(LocalDate.now()))
        }
        Regex("\\b(\\d{4})\\b").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { year ->
            return StreamingCommunityAvailability(true, year.toString(), StreamingCommunityAvailability.Precision.YEAR, year > Year.now().value)
        }
        return StreamingCommunityAvailability(true, text, StreamingCommunityAvailability.Precision.UNKNOWN)
    }

    fun isUpcoming(status: String?, releaseDate: String?): Boolean {
        val statusToken = normalize(status)
        if (statusToken in releasedTokens) return false
        if (statusToken in upcomingTokens) return true
        return parseDate(releaseDate).isFuture
    }

    fun shouldProbeInconsistent(status: String?, releaseDate: String?): Boolean {
        val availability = parseDate(releaseDate)
        return availability.hasDate && !availability.isFuture && normalize(status) !in releasedTokens
    }

    private fun normalize(value: String?): String = value.orEmpty().trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")
}
