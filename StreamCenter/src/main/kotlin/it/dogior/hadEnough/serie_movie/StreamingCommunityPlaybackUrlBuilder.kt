package it.dogior.hadEnough.serie_movie

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object StreamingCommunityPlaybackUrlBuilder {
    fun movie(baseUrl: String, titleId: Int): String = "$baseUrl/iframe/$titleId?canPlayFHD=1"
    fun episode(baseUrl: String, titleId: Int, episodeId: Int): String =
        "$baseUrl/iframe/$titleId?episode_id=${URLEncoder.encode(episodeId.toString(), StandardCharsets.UTF_8.name())}&next_episode=1&canPlayFHD=1"
}
