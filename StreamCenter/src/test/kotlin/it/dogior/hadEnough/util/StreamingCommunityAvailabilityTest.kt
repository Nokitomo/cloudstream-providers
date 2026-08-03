package it.dogior.hadEnough.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingCommunityAvailabilityTest {
    @Test
    fun futureDateMarksContentUpcoming() {
        assertTrue(StreamingCommunityAvailabilityResolver.isUpcoming("", "2099-01-01"))
        assertFalse(StreamingCommunityAvailabilityResolver.isUpcoming("released", "2099-01-01"))
    }

    @Test
    fun inconsistentPastDateCanBeProbed() {
        assertTrue(StreamingCommunityAvailabilityResolver.shouldProbeInconsistent("planned", "2020-01-01"))
        assertFalse(StreamingCommunityAvailabilityResolver.shouldProbeInconsistent("ended", "2020-01-01"))
    }

    @Test
    fun playableSourceClearsUpcomingFlag() {
        assertFalse(StreamingCommunityAvailabilityResolver.shouldKeepUpcoming("planned", "2020-01-01", true))
        assertTrue(StreamingCommunityAvailabilityResolver.shouldKeepUpcoming("planned", "2020-01-01", false))
    }
}
