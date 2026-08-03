package it.dogior.hadEnough.serie_movie

import org.jsoup.Jsoup
import java.net.URI

internal object StreamingCommunityEmbedResolver {
    fun resolveEmbedUrl(html: String, baseUrl: String): String? {
        val page = StreamingCommunityPayloadParser.parseObject(html)?.optJSONObject("props")?.optString("embedUrl")?.trim()
        if (!page.isNullOrBlank()) return absolute(page, baseUrl)
        val href = Jsoup.parse(html, baseUrl).selectFirst("a[href*='/it/iframe/']")?.attr("href")
        if (!href.isNullOrBlank()) return absolute(href, baseUrl)
        Regex("https?://[^\"'\\s]+/it/iframe/\\d+[^\"'\\s]*", RegexOption.IGNORE_CASE).find(html)?.value?.let { return it }
        return Regex("/it/iframe/\\d+[^\"'\\s]*", RegexOption.IGNORE_CASE).find(html)?.value?.let { absolute(it, baseUrl) }
    }

    fun resolveIframeUrl(html: String, baseUrl: String): String? {
        val src = Jsoup.parse(html, baseUrl).selectFirst("iframe[src]")?.attr("src")
        if (!src.isNullOrBlank()) return absolute(src, baseUrl)
        return Regex("https?://[^\"'\\s]+vixcloud\\.co/(?:embed|playlist)/\\d+[^\"'\\s]*", RegexOption.IGNORE_CASE).find(html)?.value
    }

    private fun absolute(value: String, baseUrl: String): String = URI(baseUrl).resolve(value.trim()).toString()
}
