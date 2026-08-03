package it.dogior.hadEnough.serie_movie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityTitleResolverTest {
    @Test
    fun prefersLocalizedNameAndFallsBackToSlug() {
        assertEquals("Titolo Italiano", StreamingCommunityTitleResolver.resolve(JSONObject("{\"name\":\"Original\",\"slug\":\"original\",\"translations\":[{\"key\":\"name\",\"locale\":\"it\",\"value\":\"Titolo Italiano\"}]}")))
        assertEquals("Fallback Title", StreamingCommunityTitleResolver.resolve(JSONObject("{\"slug\":\"fallback-title\"}")))
    }
}
