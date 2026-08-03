package it.dogior.hadEnough

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VixCloudStreamParserTest {
    @Test
    fun parsesMultipleServersAndDownloadUrl() {
        val html = """
            <script>
            window.masterPlaylist = { url: '/playlist/42?b=1', params: { token: 'token', expires: 'expires', asn: 'asn' } };
            window.canPlayFHD = true;
            window.streams = [{name:'Primary',url:'/playlist/42?ub=1'}];
            window.downloadUrl = '/download.mp4';
            </script>
        """.trimIndent()

        val streams = VixCloudStreamParser.parse(html, "https://vixcloud.co/embed/42")

        assertEquals(4, streams.size)
        assertTrue(streams.any { it.label == "Primary" && it.url.contains("token=token") })
        assertTrue(streams.any { it.label == "Server1" && it.url.contains("ub=1") })
        assertTrue(streams.any { it.label == "Server2" && it.url.contains("ab=1") })
        assertTrue(streams.any { it.isDownload && it.url == "https://vixcloud.co/download.mp4" })
        assertTrue(streams.filterNot { it.isDownload }.all { it.url.contains("h=1") })
    }

    @Test
    fun usesEmbedParametersAndPlaylistFallback() {
        val streams = VixCloudStreamParser.parse(
            "<script>window.canPlayFHD = false;</script>",
            "https://vixcloud.co/embed/99?token=fromEmbed&expires=123",
        )

        assertEquals(2, streams.size)
        assertTrue(streams.all { it.url.startsWith("https://vixcloud.co/playlist/99") })
        assertTrue(streams.all { it.url.contains("token=fromEmbed") && it.url.contains("expires=123") })
    }
}
