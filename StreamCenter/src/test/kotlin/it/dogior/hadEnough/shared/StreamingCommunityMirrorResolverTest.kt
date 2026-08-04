package it.dogior.hadEnough.shared

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingCommunityMirrorResolverTest {
    @Test
    fun `accepts valid Inertia archive HTML`() {
        val html = """
            <html><body>
            <div id="app" data-page="{&amp;quot;component&amp;quot;:&amp;quot;Titles/Browse&amp;quot;,&amp;quot;props&amp;quot;:{&amp;quot;titles&amp;quot;:{&amp;quot;data&amp;quot;:[]}}}"></div>
            </body></html>
        """.trimIndent()

        assertTrue(StreamingCommunityMirrorResolver.isValidArchivePayload(html))
    }

    @Test
    fun `accepts valid Inertia JSON response`() {
        assertTrue(
            StreamingCommunityMirrorResolver.isValidArchivePayload(
                """{"component":"Titles/Browse","props":{"titles":{"data":[]}}}""",
            ),
        )
    }

    @Test
    fun `rejects generic success pages and incomplete payloads`() {
        assertFalse(StreamingCommunityMirrorResolver.isValidArchivePayload("<html>OK</html>"))
        assertFalse(
            StreamingCommunityMirrorResolver.isValidArchivePayload(
                """{"component":"Home","props":{"titles":[]}}""",
            ),
        )
        assertFalse(
            StreamingCommunityMirrorResolver.isValidArchivePayload(
                """{"component":"Titles/Browse","props":{}}""",
            ),
        )
    }

    @Test
    fun `falls back to the second mirror when the primary is unhealthy`() = runBlocking {
        StreamingCommunityMirrorResolver.clearMemoryCache()
        val requestedUrls = mutableListOf<String>()
        val validPayload = """{"component":"Titles/Browse","props":{"titles":{"data":[]}}}"""

        val resolved = StreamingCommunityMirrorResolver.resolveWithProbe(
            preferences = null,
            cacheKey = "fallback-test",
            candidates = listOf(
                StreamingCommunityMirrorCandidate(
                    PastebinSite.STREAMING_UNITY,
                    "https://streamingunity.test",
                ),
                StreamingCommunityMirrorCandidate(
                    PastebinSite.STREAMING_COMMUNITY,
                    "https://streamingcommunityz.test",
                ),
            ),
            now = 1_000L,
        ) { url ->
            requestedUrls += url
            if (url.contains("streamingunity")) {
                StreamingCommunityMirrorResolver.ProbeResponse(503, url, "unavailable")
            } else {
                StreamingCommunityMirrorResolver.ProbeResponse(
                    200,
                    "https://streamingcommunityz.new/it/archive",
                    validPayload,
                )
            }
        }

        assertEquals("https://streamingcommunityz.new", resolved)
        assertEquals(
            listOf(
                "https://streamingunity.test/it/archive",
                "https://streamingcommunityz.test/it/archive",
            ),
            requestedUrls,
        )
    }

    @Test
    fun `does not accept an unrelated redirect from a healthy response`() = runBlocking {
        StreamingCommunityMirrorResolver.clearMemoryCache()
        val validPayload = """{"component":"Titles/Browse","props":{"titles":[]}}"""

        val resolved = StreamingCommunityMirrorResolver.resolveWithProbe(
            preferences = null,
            cacheKey = "redirect-validation-test",
            candidates = listOf(
                StreamingCommunityMirrorCandidate(
                    PastebinSite.STREAMING_UNITY,
                    "https://streamingunity.test",
                ),
            ),
            now = 1_000L,
        ) { url ->
            StreamingCommunityMirrorResolver.ProbeResponse(
                200,
                "https://advertising.example/landing",
                validPayload,
            )
        }

        assertEquals("https://streamingunity.test", resolved)
    }
}
