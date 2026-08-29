package takagi.ru.monica.github.domain

/**
 * Relative timestamp buckets for GitHub's ISO-8601 instants.
 *
 * The domain layer stays KMP-shareable, so parsing and bucketing are hand written
 * instead of delegating to java.time. Rendering maps these buckets to localized
 * resources in the UI layer.
 */
sealed interface GithubRelativeTime {
    data object JustNow : GithubRelativeTime

    data class Minutes(val value: Int) : GithubRelativeTime

    data class Hours(val value: Int) : GithubRelativeTime

    data class Days(val value: Int) : GithubRelativeTime

    data class Months(val value: Int) : GithubRelativeTime

    data class Years(val value: Int) : GithubRelativeTime

    /** Far past, far future, or unparsable input: callers fall back to the calendar date. */
    data class AbsoluteDate(val isoDate: String) : GithubRelativeTime
}

object GithubTimestamps {
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
    private const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR

    /** Below this the exact minute count carries no information. */
    private const val JUST_NOW_SECONDS = 45L

    /** GitHub never returns dates before its own launch; anything earlier is a parse artifact. */
    private const val MINIMUM_YEAR = 2007

    /**
     * Buckets [isoTimestamp] relative to [nowEpochSeconds].
     *
     * Future timestamps and unparsable input degrade to [GithubRelativeTime.AbsoluteDate] so the
     * UI can keep showing the calendar date instead of inventing a negative interval. Clock skew
     * of up to a minute is tolerated and reported as "just now".
     */
    fun relativeTo(isoTimestamp: String, nowEpochSeconds: Long): GithubRelativeTime {
        val epochSeconds = parseEpochSeconds(isoTimestamp)
            ?: return GithubRelativeTime.AbsoluteDate(isoDate(isoTimestamp))
        val elapsed = nowEpochSeconds - epochSeconds
        if (elapsed < -SECONDS_PER_MINUTE) {
            return GithubRelativeTime.AbsoluteDate(isoDate(isoTimestamp))
        }
        if (elapsed < JUST_NOW_SECONDS) return GithubRelativeTime.JustNow

        val minutes = elapsed / SECONDS_PER_MINUTE
        if (minutes < 60L) return GithubRelativeTime.Minutes(minutes.toInt())

        val hours = elapsed / SECONDS_PER_HOUR
        if (hours < 24L) return GithubRelativeTime.Hours(hours.toInt())

        val days = elapsed / SECONDS_PER_DAY
        if (days < 30L) return GithubRelativeTime.Days(days.toInt())

        val months = days / 30L
        if (months < 12L) return GithubRelativeTime.Months(months.toInt())

        val years = days / 365L
        return if (years < 1L) {
            GithubRelativeTime.Months(months.toInt())
        } else {
            GithubRelativeTime.Years(years.toInt())
        }
    }

    /** The leading `yyyy-MM-dd` of an ISO-8601 timestamp, or the trimmed input when malformed. */
    fun isoDate(isoTimestamp: String): String {
        val trimmed = isoTimestamp.trim()
        val separator = trimmed.indexOf('T')
        return if (separator == 10) trimmed.take(10) else trimmed
    }

    /**
     * Parses the `yyyy-MM-ddTHH:mm:ssZ` form GitHub returns. Fractional seconds are accepted and
     * discarded; numeric offsets are applied. Returns null for anything else.
     */
    fun parseEpochSeconds(isoTimestamp: String): Long? {
        val text = isoTimestamp.trim()
        if (text.length < 19) return null
        if (text[4] != '-' || text[7] != '-') return null
        if (text[10] != 'T' && text[10] != 't' && text[10] != ' ') return null
        if (text[13] != ':' || text[16] != ':') return null

        val year = text.substring(0, 4).toIntOrNull() ?: return null
        val month = text.substring(5, 7).toIntOrNull() ?: return null
        val day = text.substring(8, 10).toIntOrNull() ?: return null
        val hour = text.substring(11, 13).toIntOrNull() ?: return null
        val minute = text.substring(14, 16).toIntOrNull() ?: return null
        val second = text.substring(17, 19).toIntOrNull() ?: return null

        if (year < MINIMUM_YEAR) return null
        if (month !in 1..12 || day !in 1..daysInMonth(year, month)) return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null

        val offsetSeconds = parseOffsetSeconds(text) ?: return null
        val days = daysFromEpoch(year, month, day)
        val timeOfDay = hour * SECONDS_PER_HOUR + minute * SECONDS_PER_MINUTE + second.toLong()
        return days * SECONDS_PER_DAY + timeOfDay - offsetSeconds
    }

    /** Seconds to subtract to reach UTC. `Z`, a missing zone, and `+HH:mm` forms are supported. */
    private fun parseOffsetSeconds(text: String): Long? {
        val zone = text.substring(19).let { remainder ->
            // Fractional seconds precede the zone designator and carry no offset information.
            if (remainder.startsWith('.')) remainder.dropWhile { it == '.' || it.isDigit() } else remainder
        }
        if (zone.isEmpty() || zone == "Z" || zone == "z") return 0L

        val sign = when (zone[0]) {
            '+' -> 1L
            '-' -> -1L
            else -> return null
        }
        val digits = zone.substring(1).filter { it != ':' }
        if (digits.length != 4 || digits.any { !it.isDigit() }) return null
        val offsetHour = digits.substring(0, 2).toIntOrNull() ?: return null
        val offsetMinute = digits.substring(2, 4).toIntOrNull() ?: return null
        if (offsetHour > 18 || offsetMinute > 59) return null
        return sign * (offsetHour * SECONDS_PER_HOUR + offsetMinute * SECONDS_PER_MINUTE)
    }

    private fun daysFromEpoch(year: Int, month: Int, day: Int): Long {
        // Shift the year so leap days land at the end of the cycle, which removes the
        // February special case from the century arithmetic below.
        val shiftedYear = if (month <= 2) year - 1 else year
        val era = (if (shiftedYear >= 0) shiftedYear else shiftedYear - 399) / 400
        val yearOfEra = shiftedYear - era * 400
        val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365L + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146097L + dayOfEra - 719468L
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isLeapYear(year)) 29 else 28
    }

    private fun isLeapYear(year: Int): Boolean =
        year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}
