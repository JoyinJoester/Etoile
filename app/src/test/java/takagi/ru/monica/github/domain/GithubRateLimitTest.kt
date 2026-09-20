package takagi.ru.monica.github.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GithubRateLimitTest {
    @Test
    fun healthyBucketsProduceNoWarning() {
        val snapshots = mapOf(
            "core" to snapshot("core", limit = 5000, remaining = 4000),
            "search" to snapshot("search", limit = 30, remaining = 25)
        )

        assertEquals(emptyList<GithubRateLimitSnapshot>(), visibleRateLimits(snapshots))
    }

    @Test
    fun exhaustedSearchWarnsEvenWhileCoreIsHealthy() {
        val snapshots = mapOf(
            "core" to snapshot("core", limit = 5000, remaining = 4000),
            "search" to snapshot("search", limit = 10, remaining = 0)
        )

        assertEquals(listOf("search"), resources(visibleRateLimits(snapshots)))
    }

    @Test
    fun exhaustedBucketIsListedBeforeBucketsThatStillAllowRequests() {
        val snapshots = mapOf(
            "core" to snapshot("core", limit = 5000, remaining = 9, resetAtEpochSeconds = 1000),
            "search" to snapshot("search", limit = 10, remaining = 0, resetAtEpochSeconds = 2000)
        )

        assertEquals(listOf("search", "core"), resources(visibleRateLimits(snapshots)))
    }

    @Test
    fun bucketWithFewestRequestsLeftLeadsRegardlessOfItsLimit() {
        val snapshots = mapOf(
            "core" to snapshot("core", limit = 5000, remaining = 120),
            "search" to snapshot("search", limit = 10, remaining = 4)
        )

        assertEquals(listOf("search", "core"), resources(visibleRateLimits(snapshots)))
    }

    private fun resources(snapshots: List<GithubRateLimitSnapshot>) = snapshots.map { it.resource }

    private fun snapshot(
        resource: String,
        limit: Int,
        remaining: Int,
        resetAtEpochSeconds: Long = 1000
    ) = GithubRateLimitSnapshot(
        resource = resource,
        limit = limit,
        remaining = remaining,
        used = limit - remaining,
        resetAtEpochSeconds = resetAtEpochSeconds
    )
}
