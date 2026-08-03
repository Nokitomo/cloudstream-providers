package it.dogior.hadEnough

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class VixCloudExtractor(
    private val sourceName: String = "VixCloud",
    private val displayName: String = "AnimeUnity",
) : ExtractorApi() {
    override val mainUrl = "vixcloud.co"
    override val name = "VixCloud"
    override val requiresReferer = false
    private val headers = mutableMapOf(
        "Accept" to "*/*",
        "Connection" to "keep-alive",
        "Cache-Control" to "no-cache",
        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
    )

    private companion object {
        const val LOG_TAG = "VixCloudExtractor"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(LOG_TAG, "REFERER: $referer  URL: $url")
        val document = app.get(url, headers = headers).document
        val html = document.select("script").firstOrNull { it.data().contains("masterPlaylist") }?.data().orEmpty()
        if (html.isBlank()) error("Missing VixCloud stream script")
        VixCloudStreamParser.parse(html, url).forEach { stream ->
            Log.d(LOG_TAG, "FINAL URL: ${stream.url}")
            callback(
                newExtractorLink(
                    source = "$sourceName ${stream.label}",
                    name = "$displayName ${stream.label}",
                    url = stream.url,
                    type = if (stream.isDownload && !stream.url.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8,
                ) {
                    this.headers = this@VixCloudExtractor.headers
                }
            )
        }
    }
}
