package it.dogior.hadEnough.extractor

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class StreamCenterVixCloudExtractor(
    private val sourceName: String = "VixCloud",
    private val displayName: String = "AnimeUnity",
) : ExtractorApi() {
    override val mainUrl = "vixcloud.co"
    override val name = "StreamCenterVixCloud"
    override val requiresReferer = false
    private val headers = mapOf(
        "Accept" to "*/*",
        "Connection" to "keep-alive",
        "Cache-Control" to "no-cache",
        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val document = app.get(url, headers = headers).document
        val html = document.select("script").firstOrNull { it.data().contains("masterPlaylist") }?.data().orEmpty()
        if (html.isBlank()) error("Missing VixCloud stream script")
        StreamCenterVixStreamParser.parse(html, url).forEach { stream ->
            callback(
                newExtractorLink(
                    source = "$sourceName ${stream.label}",
                    name = "$displayName ${stream.label}",
                    url = stream.url,
                    type = if (stream.isDownload && !stream.url.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8,
                ) {
                    this.headers = this@StreamCenterVixCloudExtractor.headers
                }
            )
        }
    }
}
