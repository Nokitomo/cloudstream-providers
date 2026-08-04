package it.dogior.hadEnough.stremio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCenterStremioCatalogLogoTest {
    @Test
    fun parsesContentLogoSeparatelyFromAddonLogo() {
        val addon = StreamCenterStremioAddon(
            key = "test",
            manifestUrl = "https://addon.example/manifest.json",
            id = "test.addon",
            name = "Test",
            version = "1.0.0",
            logoUrl = "https://addon.example/icon.png",
        )
        val item = StreamCenterStremioAddonClient.parseCatalogItem(
            addon = addon,
            root = JSONObject(
                """{"id":"tt2560140","type":"series","name":"Attack on Titan","logo":"https://images.example/title-logo.png"}""",
            ),
            fallbackType = "series",
        )

        assertEquals("https://images.example/title-logo.png", item?.logoUrl)
        assertEquals("https://addon.example/icon.png", addon.logoUrl)
    }
}
