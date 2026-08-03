package it.dogior.hadEnough.serie_movie

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.json.JSONObject

internal object StreamingCommunityPayloadParser {
    fun parseObject(payload: String): JSONObject? {
        val text = payload.trim()
        if (text.isBlank()) return null
        if (text.startsWith("{")) return runCatching { JSONObject(text) }.getOrNull()
        val pageJson = extractPageJson(text) ?: return null
        return runCatching { JSONObject(pageJson) }.getOrNull()
    }

    fun extractPageJson(html: String): String? {
        val raw = Jsoup.parse(html)
            .select("#app[data-page], [data-page]")
            .firstOrNull()
            ?.attr("data-page")
            ?.takeIf(String::isNotBlank)
            ?: return null
        val decoded = Parser.unescapeEntities(raw, true)
        return Parser.unescapeEntities(decoded, true)
    }
}
