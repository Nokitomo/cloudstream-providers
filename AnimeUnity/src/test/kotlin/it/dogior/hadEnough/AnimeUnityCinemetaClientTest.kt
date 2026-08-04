package it.dogior.hadEnough

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import it.dogior.hadEnough.shared.AnimeTitleLogoResolver

class AnimeUnityCinemetaClientTest {
    @Test
    fun selectsExactTypeAndYearMatch() {
        val metas = JSONArray("[{\"id\":\"tt1111111\",\"type\":\"series\",\"name\":\"Title\",\"releaseInfo\":\"2020\"},{\"id\":\"tt2222222\",\"type\":\"series\",\"name\":\"Title\",\"releaseInfo\":\"2021\"}]")
        assertEquals("tt2222222", AnimeUnityCinemetaClient.selectImdbId(metas, "title", 2021, "series"))
    }

    @Test
    fun parsesAniZipIdsAndClearLogo() {
        val payload = """{"mappings":{"imdb_id":"tt2560140"},"images":[{"coverType":"Poster","url":"https://img/poster.jpg"},{"coverType":"Clearlogo","url":"https://img/logo.png"}]}"""
        val artwork = AnimeTitleLogoResolver.parseAniZipPayload(payload)

        assertEquals("tt2560140", artwork.imdbId)
        assertEquals("https://img/logo.png", artwork.logoUrl)
    }

    @Test
    fun parsesCinemetaLogoAndRejectsInsecureUrl() {
        assertEquals(
            "https://images.metahub.space/logo/medium/tt2560140/img",
            AnimeTitleLogoResolver.parseCinemetaLogo(
                """{"meta":{"logo":"https://images.metahub.space/logo/medium/tt2560140/img"}}""",
            ),
        )
        assertEquals(
            null,
            AnimeTitleLogoResolver.parseCinemetaLogo("""{"meta":{"logo":"http://invalid/logo.png"}}"""),
        )
    }
}
