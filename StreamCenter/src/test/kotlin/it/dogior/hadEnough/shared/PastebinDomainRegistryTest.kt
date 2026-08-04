package it.dogior.hadEnough.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PastebinDomainRegistryTest {
    private val currentPayload = """
        https://streamingunity.dog
        https://cb01uno.pics
        https://eurostreamings.forum
        https://eurostreaming.ovh
        https://altadefinizione.you
        https://guardaserietv.study
        https://www.animeworld.ac
        https://www.animeunity.so
        https://www.animesaturn.cx
        https://streamingcommunityz.us
        https://i5hs.k9vo.com
        https://1337x.to
        https://rargb.to
    """.trimIndent()

    @Test
    fun `resolves every supported provider from current payload`() {
        assertEquals("https://www.animeunity.so", PastebinDomainRegistry.resolve(currentPayload, PastebinSite.ANIME_UNITY))
        assertEquals("https://www.animeworld.ac", PastebinDomainRegistry.resolve(currentPayload, PastebinSite.ANIME_WORLD))
        assertEquals("https://www.animesaturn.cx", PastebinDomainRegistry.resolve(currentPayload, PastebinSite.ANIME_SATURN))
        assertEquals("https://streamingunity.dog", PastebinDomainRegistry.resolve(currentPayload, PastebinSite.STREAMING_UNITY))
        assertEquals("https://streamingcommunityz.us", PastebinDomainRegistry.resolve(currentPayload, PastebinSite.STREAMING_COMMUNITY))
        assertEquals("https://altadefinizione.you", PastebinDomainRegistry.resolve(currentPayload, PastebinSite.ALTA_DEFINIZIONE))
        assertEquals("https://cb01uno.pics", PastebinDomainRegistry.resolve(currentPayload, PastebinSite.CB01))
        assertEquals("https://guardaserietv.study", PastebinDomainRegistry.resolve(currentPayload, PastebinSite.GUARDA_SERIE_TV))
        assertEquals("https://eurostreaming.ovh", PastebinDomainRegistry.resolve(currentPayload, PastebinSite.EURO_STREAMING))
    }

    @Test
    fun `named entries take priority while legacy lines remain supported`() {
        val payload = """
            https://www.animeunity.old
            animeunity=https://www.animeunity.new
        """.trimIndent()

        assertEquals("https://www.animeunity.new", PastebinDomainRegistry.resolve(payload, PastebinSite.ANIME_UNITY))
    }

    @Test
    fun `rejects unsafe or unrelated candidates`() {
        assertNull(PastebinDomainRegistry.resolve("http://www.animeunity.so", PastebinSite.ANIME_UNITY))
        assertNull(PastebinDomainRegistry.resolve("https://user:pass@www.animeunity.so", PastebinSite.ANIME_UNITY))
        assertNull(PastebinDomainRegistry.resolve("https://www.animeunity.so:8443", PastebinSite.ANIME_UNITY))
        assertNull(PastebinDomainRegistry.resolve("https://www.animeunity.so/path", PastebinSite.ANIME_UNITY))
        assertNull(PastebinDomainRegistry.resolve("https://animeunity.example.evil", PastebinSite.ANIME_UNITY))
        assertNull(PastebinDomainRegistry.resolve("https://example.com", PastebinSite.ANIME_UNITY))
        assertNull(PastebinDomainRegistry.resolve("x".repeat(16 * 1024 + 1), PastebinSite.ANIME_UNITY))
        assertNull(
            PastebinDomainRegistry.resolve(
                List(129) { "https://www.animeunity.so" }.joinToString("\n"),
                PastebinSite.ANIME_UNITY,
            ),
        )
    }

    @Test
    fun `normalizes configured fallback without requiring a registered host`() {
        assertEquals("https://custom.example", PastebinDomainRegistry.normalizeFallback("custom.example/"))
        assertNull(PastebinDomainRegistry.normalizeFallback("http://custom.example"))
    }

    @Test
    fun `rebases stale provider links and preserves path and query`() {
        assertEquals(
            "https://www.animeunity.new/anime/42-title?episode=1",
            PastebinDomainRegistry.rebaseUrl(
                "https://www.animeunity.old/anime/42-title?episode=1",
                PastebinSite.ANIME_UNITY,
                "https://www.animeunity.new",
            ),
        )
        assertEquals(
            "https://external.example/video",
            PastebinDomainRegistry.rebaseUrl(
                "https://external.example/video",
                PastebinSite.ANIME_UNITY,
                "https://www.animeunity.new",
            ),
        )
    }
}
