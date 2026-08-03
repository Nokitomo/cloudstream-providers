package it.dogior.hadEnough

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityCdnResolverTest {
    @Test
    fun prefersExplicitCdnAndDerivesFallback() {
        assertEquals("https://assets.example", StreamingCommunityCdnResolver.resolve("assets.example/", "https://streamingunity.cc/it"))
        assertEquals("https://cdn.streamingunity.cc", StreamingCommunityCdnResolver.resolve(null, "https://streamingunity.cc/it"))
    }
}
