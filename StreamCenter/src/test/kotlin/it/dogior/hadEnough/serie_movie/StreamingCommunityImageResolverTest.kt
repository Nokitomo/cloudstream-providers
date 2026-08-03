package it.dogior.hadEnough.serie_movie

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityImageResolverTest {
    @Test
    fun preservesAbsoluteUrlsAndPrefixesRelativeImages() {
        assertEquals("https://img.example/poster.jpg", StreamingCommunityImageResolver.resolve("https://img.example/poster.jpg", "https://cdn.example"))
        assertEquals("https://cdn.example/images/poster.jpg", StreamingCommunityImageResolver.resolve("/poster.jpg", "https://cdn.example"))
    }
}
