package it.dogior.hadEnough

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeUnityCinemetaClientTest {
    @Test
    fun selectsExactTypeAndYearMatch() {
        val metas = JSONArray("[{\"id\":\"tt1111111\",\"type\":\"series\",\"name\":\"Title\",\"releaseInfo\":\"2020\"},{\"id\":\"tt2222222\",\"type\":\"series\",\"name\":\"Title\",\"releaseInfo\":\"2021\"}]")
        assertEquals("tt2222222", AnimeUnityCinemetaClient.selectImdbId(metas, "title", 2021, "series"))
    }
}
