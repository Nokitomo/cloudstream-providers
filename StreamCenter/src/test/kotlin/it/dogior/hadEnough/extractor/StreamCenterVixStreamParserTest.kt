package it.dogior.hadEnough.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCenterVixStreamParserTest {
    @Test
    fun parsesServersAndDownloadUrl() {
        val html = """
            <script>
            window.masterPlaylist = { url: '/playlist/42?b=1', params: { token: 'token', expires: 'expires', asn: 'asn' } };
            window.canPlayFHD = true;
            window.streams = [{name:'Primary',url:'/playlist/42?ub=1'}];
            window.downloadUrl = '/download.mp4';
            </script>
        """.trimIndent()

        val streams = StreamCenterVixStreamParser.parse(html, "https://vixcloud.co/embed/42")

        assertEquals(4, streams.size)
        assertTrue(streams.any { it.label == "Primary" && it.url.contains("token=token") })
        assertTrue(streams.any { it.label == "Server1" && it.url.contains("ub=1") })
        assertTrue(streams.any { it.label == "Server2" && it.url.contains("ab=1") })
        assertTrue(streams.any { it.isDownload && it.url == "https://vixcloud.co/download.mp4" })
    }
}
