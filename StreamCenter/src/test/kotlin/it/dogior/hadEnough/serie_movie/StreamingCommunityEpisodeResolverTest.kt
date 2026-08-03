package it.dogior.hadEnough.serie_movie

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityEpisodeResolverTest {
    @Test
    fun prefersItalianNameThenRawNameThenNumberFallback() {
        assertEquals("Titolo episodio", StreamingCommunityEpisodeResolver.resolve("Original", 2, JSONArray("[{\"key\":\"name\",\"locale\":\"it\",\"value\":\"Titolo episodio\"}]")))
        assertEquals("Original", StreamingCommunityEpisodeResolver.resolve("Original", 2, null))
        assertEquals("Episode 2", StreamingCommunityEpisodeResolver.resolve(null, 2, null))
    }
}
