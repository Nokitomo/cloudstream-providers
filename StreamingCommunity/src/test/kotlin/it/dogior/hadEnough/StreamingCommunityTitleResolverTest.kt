package it.dogior.hadEnough

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityTitleResolverTest {
    @Test
    fun prefersItalianThenEnglishThenSlug() {
        assertEquals("Titolo Italiano", StreamingCommunityTitleResolver.resolve("Original", "original", listOf(TitleTranslation("name", "it", "Titolo Italiano"))))
        assertEquals("English Title", StreamingCommunityTitleResolver.resolve("Original", "english-title", listOf(TitleTranslation("name", "en", "English Title"))))
        assertEquals("Fallback Title", StreamingCommunityTitleResolver.resolve(null, "fallback-title", emptyList()))
    }
}
