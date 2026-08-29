package takagi.ru.monica.github.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubRelativeTimeTest {
    @Test
    fun parsesGithubUtcTimestamps() {
        assertEquals(1_767_225_600L, GithubTimestamps.parseEpochSeconds("2026-01-01T00:00:00Z"))
        assertEquals(1_193_875_200L, GithubTimestamps.parseEpochSeconds("2007-11-01T00:00:00Z"))
    }

    @Test
    fun rejectsDatesBeforeGithubExisted() {
        assertNull(GithubTimestamps.parseEpochSeconds("1970-01-01T00:00:00Z"))
        assertNull(GithubTimestamps.parseEpochSeconds("0001-01-01T00:00:00Z"))
    }

    @Test
    fun appliesNumericZoneOffsets() {
        val utc = GithubTimestamps.parseEpochSeconds("2026-08-29T10:00:00Z")
        assertEquals(utc, GithubTimestamps.parseEpochSeconds("2026-08-29T18:00:00+08:00"))
        assertEquals(utc, GithubTimestamps.parseEpochSeconds("2026-08-29T18:00:00+0800"))
        assertEquals(utc, GithubTimestamps.parseEpochSeconds("2026-08-29T05:30:00-04:30"))
    }

    @Test
    fun acceptsFractionalSecondsWithoutShiftingTheInstant() {
        assertEquals(
            GithubTimestamps.parseEpochSeconds("2026-08-29T10:00:00Z"),
            GithubTimestamps.parseEpochSeconds("2026-08-29T10:00:00.123456Z")
        )
    }

    @Test
    fun handlesLeapDaysAndMonthBoundaries() {
        val leapDay = GithubTimestamps.parseEpochSeconds("2024-02-29T00:00:00Z")
        val marchFirst = GithubTimestamps.parseEpochSeconds("2024-03-01T00:00:00Z")
        assertEquals(86_400L, marchFirst!! - leapDay!!)
        assertNull(GithubTimestamps.parseEpochSeconds("2023-02-29T00:00:00Z"))
    }

    @Test
    fun rejectsMalformedInput() {
        assertNull(GithubTimestamps.parseEpochSeconds(""))
        assertNull(GithubTimestamps.parseEpochSeconds("2026-08-29"))
        assertNull(GithubTimestamps.parseEpochSeconds("not-a-timestamp-at-all"))
        assertNull(GithubTimestamps.parseEpochSeconds("2026-13-01T00:00:00Z"))
        assertNull(GithubTimestamps.parseEpochSeconds("2026-08-29T25:00:00Z"))
        assertNull(GithubTimestamps.parseEpochSeconds("2026-08-29T10:00:00~05:00"))
    }

    @Test
    fun bucketsElapsedTimeByMagnitude() {
        val now = GithubTimestamps.parseEpochSeconds("2026-08-29T12:00:00Z")!!
        assertEquals(GithubRelativeTime.JustNow, relative("2026-08-29T11:59:30Z", now))
        assertEquals(GithubRelativeTime.Minutes(5), relative("2026-08-29T11:55:00Z", now))
        assertEquals(GithubRelativeTime.Hours(3), relative("2026-08-29T09:00:00Z", now))
        assertEquals(GithubRelativeTime.Days(3), relative("2026-08-26T12:00:00Z", now))
        assertEquals(GithubRelativeTime.Months(2), relative("2026-06-25T12:00:00Z", now))
        assertEquals(GithubRelativeTime.Years(2), relative("2024-08-29T12:00:00Z", now))
    }

    @Test
    fun roundsDownAtBucketBoundaries() {
        val now = GithubTimestamps.parseEpochSeconds("2026-08-29T12:00:00Z")!!
        assertEquals(GithubRelativeTime.Minutes(59), relative("2026-08-29T11:00:30Z", now))
        assertEquals(GithubRelativeTime.Hours(1), relative("2026-08-29T11:00:00Z", now))
        assertEquals(GithubRelativeTime.Hours(23), relative("2026-08-28T12:30:00Z", now))
        assertEquals(GithubRelativeTime.Days(1), relative("2026-08-28T12:00:00Z", now))
        assertEquals(GithubRelativeTime.Days(29), relative("2026-07-31T12:00:00Z", now))
        assertEquals(GithubRelativeTime.Months(1), relative("2026-07-30T12:00:00Z", now))
    }

    @Test
    fun toleratesSmallClockSkewButNotRealFutureDates() {
        val now = GithubTimestamps.parseEpochSeconds("2026-08-29T12:00:00Z")!!
        assertEquals(GithubRelativeTime.JustNow, relative("2026-08-29T12:00:30Z", now))
        assertEquals(
            GithubRelativeTime.AbsoluteDate("2027-01-01"),
            relative("2027-01-01T00:00:00Z", now)
        )
    }

    @Test
    fun unparsableTimestampsFallBackToTheCalendarDate() {
        val now = GithubTimestamps.parseEpochSeconds("2026-08-29T12:00:00Z")!!
        assertEquals(
            GithubRelativeTime.AbsoluteDate("2026-08-29"),
            relative("2026-08-29", now)
        )
        assertEquals(GithubRelativeTime.AbsoluteDate(""), relative("", now))
    }

    @Test
    fun isoDateKeepsMalformedInputVisible() {
        assertEquals("2026-08-29", GithubTimestamps.isoDate("2026-08-29T10:56:00Z"))
        assertEquals("2026-08-29", GithubTimestamps.isoDate("  2026-08-29T10:56:00Z  "))
        assertEquals("29/08/2026", GithubTimestamps.isoDate("29/08/2026"))
    }

    private fun relative(isoTimestamp: String, nowEpochSeconds: Long): GithubRelativeTime =
        GithubTimestamps.relativeTo(isoTimestamp, nowEpochSeconds)
}
