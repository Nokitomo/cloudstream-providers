package it.dogior.hadEnough.serie_movie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StreamingCommunityPayloadParserTest {
    @Test
    fun parsesDirectJsonAndAlternateDataPageWrapper() {
        assertNotNull(StreamingCommunityPayloadParser.parseObject("{\"props\":{\"titles\":[]}}"))
        val html = "<section data-page=\"{&amp;quot;props&amp;quot;:{&amp;quot;cdn_url&amp;quot;:&amp;quot;https://cdn.example&amp;quot;}}\"></section>"
        val result = StreamingCommunityPayloadParser.parseObject(html)
        assertEquals("https://cdn.example", result?.optJSONObject("props")?.optString("cdn_url"))
    }
}
