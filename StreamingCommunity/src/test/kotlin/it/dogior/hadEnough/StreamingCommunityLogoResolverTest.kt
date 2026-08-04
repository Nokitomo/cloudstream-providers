package it.dogior.hadEnough

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityLogoResolverTest {
    @Test
    fun prefersItalianLogoThenNeutralLogo() {
        val images = listOf(
            PosterImage(filename = "english.png", type = "logo", lang = "en"),
            PosterImage(filename = "neutral.png", type = "logo"),
            PosterImage(filename = "italian.png", type = "logo", lang = "it"),
        )

        assertEquals("italian.png", selectLogoImage(images)?.value())
        assertEquals("neutral.png", selectLogoImage(images.dropLast(1))?.value())
    }

    @Test
    fun ignoresNonLogoImagesAndEmptyValues() {
        val images = listOf(
            PosterImage(filename = "poster.jpg", type = "poster", lang = "it"),
            PosterImage(type = "logo", lang = "it"),
        )

        assertEquals(null, selectLogoImage(images))
    }
}
