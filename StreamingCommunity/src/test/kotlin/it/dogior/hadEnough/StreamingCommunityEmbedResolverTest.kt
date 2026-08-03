package it.dogior.hadEnough

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityEmbedResolverTest {
    @Test
    fun resolvesPayloadAnchorsAndIframeFallbacks() {
        assertEquals("https://streamingunity.cc/it/iframe/12", StreamingCommunityEmbedResolver.resolveEmbedUrl("<a href=\"/it/iframe/12\">play</a>", "https://streamingunity.cc/it/watch/3"))
        assertEquals("https://vixcloud.co/embed/99", StreamingCommunityEmbedResolver.resolveIframeUrl("<iframe src=\"https://vixcloud.co/embed/99\"></iframe>", "https://streamingunity.cc/it/iframe/12"))
    }
}
