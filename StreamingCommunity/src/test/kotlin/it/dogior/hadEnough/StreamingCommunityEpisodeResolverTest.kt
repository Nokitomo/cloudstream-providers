package it.dogior.hadEnough

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityEpisodeResolverTest {
    @Test
    fun prefersItalianNameThenRawNameThenNumberFallback() {
        assertEquals("Titolo episodio", StreamingCommunityEpisodeResolver.resolve("Original", 2, listOf(TitleTranslation("name", "it", "Titolo episodio"))))
        assertEquals("Original", StreamingCommunityEpisodeResolver.resolve("Original", 2, emptyList()))
        assertEquals("Episode 2", StreamingCommunityEpisodeResolver.resolve(null, 2, emptyList()))
    }
}
