package it.dogior.hadEnough

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityArchiveFiltersTest {
    @Test
    fun archiveFilterDefaultsRemainCompatible() {
        val filters = StreamCenterTvArchiveFilters(
            minimumViews = 100000,
            service = "netflix",
            quality = "HD",
            minimumAge = 16,
        )
        assertEquals(100000, filters.minimumViews)
        assertEquals("netflix", filters.service)
        assertEquals("HD", filters.quality)
        assertEquals(16, filters.minimumAge)
    }
}
