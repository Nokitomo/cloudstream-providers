package it.dogior.hadEnough

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StreamingCommunityPayloadParserTest {
    @Test
    fun parsesDirectJsonPayload() {
        val result = StreamingCommunityPayloadParser.parseObject("{\"props\":{\"titles\":[]}}")
        assertNotNull(result)
        assertNotNull(result?.optJSONObject("props"))
    }

    @Test
    fun parsesDataPageFromNonAppWrapperAndDoubleEscapedEntities() {
        val html = "<div data-page=\"{&amp;quot;props&amp;quot;:{&amp;quot;cdn_url&amp;quot;:&amp;quot;https://cdn.example&amp;quot;}}\"></div>"
        val result = StreamingCommunityPayloadParser.parseObject(html)
        assertEquals("https://cdn.example", result?.optJSONObject("props")?.optString("cdn_url"))
    }
}
