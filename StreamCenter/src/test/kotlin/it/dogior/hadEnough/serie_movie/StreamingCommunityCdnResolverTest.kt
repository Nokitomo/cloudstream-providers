package it.dogior.hadEnough.serie_movie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityCdnResolverTest {
    @Test
    fun acceptsCdnAliasesAndDerivesFallback() {
        assertEquals("https://assets.example", StreamingCommunityCdnResolver.resolve(JSONObject("{\"cdnUrl\":\"assets.example/\"}"), "https://streamingunity.cc/it"))
        assertEquals("https://cdn.streamingunity.cc", StreamingCommunityCdnResolver.resolve(JSONObject(), "https://streamingunity.cc/it"))
    }
}
