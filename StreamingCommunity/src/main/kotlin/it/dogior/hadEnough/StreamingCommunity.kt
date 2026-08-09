package it.dogior.hadEnough

import android.content.SharedPreferences
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import it.dogior.hadEnough.shared.PastebinDomainRegistry
import it.dogior.hadEnough.shared.PastebinDomainResolver
import it.dogior.hadEnough.shared.PastebinSite
import it.dogior.hadEnough.shared.ProviderRedirectResolver
import it.dogior.hadEnough.shared.StreamingCommunityMirrorCandidate
import it.dogior.hadEnough.shared.StreamingCommunityMirrorResolver

class StreamingCommunity(
    override var lang: String = "it",
    customBaseUrl: String? = null,
    val showUpcoming: Boolean = true,
    private val remoteDomainPreferences: SharedPreferences? = null,
) : MainAPI() {
    private val configuredBaseUrl = resolveBaseUrl(customBaseUrl)
    private val hasManualBaseUrl = normalizeBaseUrl(customBaseUrl) != null
    private var siteRootUrl = configuredBaseUrl
    private var siteHost = siteRootUrl.toHttpUrl().host
    private var fallbackCdnHost = resolveCdnHost(siteHost)
    private var cdnBaseUrl = "https://$fallbackCdnHost"
    private var inertiaVersion = ""
    private var decodedXsrfToken = ""
    private val headers = mapOf(
        "Cookie" to "",
        "X-Inertia" to true.toString(),
        "X-Inertia-Version" to inertiaVersion,
        "X-Requested-With" to "XMLHttpRequest",
    ).toMutableMap()

    override var mainUrl = siteRootUrl + lang
    override var name = Companion.name
    override var supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Cartoon, TvType.Documentary)
    override val hasMainPage = true

    private suspend fun ensureRemoteDomain() {
        val resolvedRoot = if (hasManualBaseUrl) {
            resolveBaseUrl(ProviderRedirectResolver.resolve(
                preferences = remoteDomainPreferences,
                site = PastebinSite.STREAMING_UNITY,
                initialUrl = configuredBaseUrl,
            ))
        } else {
            val streamingUnityUrl = PastebinDomainResolver.resolve(
                preferences = remoteDomainPreferences,
                site = PastebinSite.STREAMING_UNITY,
                configuredFallback = configuredBaseUrl,
                preferLastGoodForUnchangedCandidate = false,
            )
            val streamingCommunityUrl = PastebinDomainResolver.resolve(
                preferences = remoteDomainPreferences,
                site = PastebinSite.STREAMING_COMMUNITY,
                configuredFallback = PastebinSite.STREAMING_COMMUNITY.fallbackUrl,
                preferLastGoodForUnchangedCandidate = false,
            )
            resolveBaseUrl(StreamingCommunityMirrorResolver.resolve(
                preferences = remoteDomainPreferences,
                cacheKey = "standalone_streamingunity_primary",
                candidates = listOf(
                    StreamingCommunityMirrorCandidate(PastebinSite.STREAMING_UNITY, streamingUnityUrl),
                    StreamingCommunityMirrorCandidate(PastebinSite.STREAMING_COMMUNITY, streamingCommunityUrl),
                    StreamingCommunityMirrorCandidate(
                        PastebinSite.STREAMING_UNITY,
                        PastebinSite.STREAMING_UNITY.fallbackUrl,
                    ),
                    StreamingCommunityMirrorCandidate(
                        PastebinSite.STREAMING_COMMUNITY,
                        PastebinSite.STREAMING_COMMUNITY.fallbackUrl,
                    ),
                ),
            ))
        }
        if (resolvedRoot != siteRootUrl) {
            siteRootUrl = resolvedRoot
            siteHost = siteRootUrl.toHttpUrl().host
            fallbackCdnHost = resolveCdnHost(siteHost)
            cdnBaseUrl = "https://$fallbackCdnHost"
            mainUrl = siteRootUrl + lang
            resetSession()
        }
    }

    private fun resetSession() {
        inertiaVersion = ""
        decodedXsrfToken = ""
        headers.clear()
        headers.putAll(
            mapOf(
                "Cookie" to "",
                "X-Inertia" to true.toString(),
                "X-Inertia-Version" to "",
                "X-Requested-With" to "XMLHttpRequest",
            ),
        )
    }

    private fun rebaseProviderUrl(url: String): String {
        return PastebinDomainRegistry.rebaseUrl(url, PastebinSite.STREAMING_UNITY, siteRootUrl)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://streamingunity.vip/"
        var name = "StreamingCommunity"
        const val TAG = "SCommunity"

        fun normalizeBaseUrl(rawUrl: String?): String? {
            val trimmedValue = rawUrl?.trim().orEmpty()
            if (trimmedValue.isBlank()) return null

            val candidate = if ("://" in trimmedValue) trimmedValue else "https://$trimmedValue"

            return runCatching {
                val normalizedUrl = candidate.toHttpUrl()
                val rewrittenHost = normalizeKnownHost(normalizedUrl.host)

                normalizedUrl.newBuilder()
                    .host(rewrittenHost)
                    .encodedPath("/")
                    .query(null)
                    .fragment(null)
                    .build()
                    .toString()
            }.getOrNull()
        }

        fun resolveBaseUrl(rawUrl: String?): String {
            return normalizeBaseUrl(rawUrl) ?: DEFAULT_BASE_URL
        }

        private fun resolveCdnHost(siteHost: String): String {
            val fallbackHost = DEFAULT_BASE_URL.toHttpUrl().host
            return if (isIpAddress(siteHost) || siteHost.equals("localhost", ignoreCase = true)) {
                "cdn.$fallbackHost"
            } else {
                "cdn.$siteHost"
            }
        }

        private fun normalizeKnownHost(host: String): String {
            return when (host.lowercase()) {
                "streamingunity.biz",
                "www.streamingunity.biz" -> DEFAULT_BASE_URL.toHttpUrl().host

                else -> host
            }
        }

        private fun isIpAddress(host: String): Boolean {
            val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
            return ipv4Regex.matches(host) || host.contains(":")
        }
    }

    override val mainPage = mainPageOf(
        SliderFetchRequestSlider(name = "top10", genre = null).toJson() to "Slider",
        SliderFetchRequestSlider(name = "trending", genre = null).toJson() to "Slider",
        SliderFetchRequestSlider(name = "latest", genre = null).toJson() to "Slider",
        SliderFetchRequestSlider(name = "upcoming", genre = null).toJson() to "Slider",
        GenreRequest(nameEN = "Animation", nameIT = "Animazione", id = 19).toJson() to "Genre",
        GenreRequest(nameEN = "Adventure", nameIT = "Avventura", id = 11).toJson() to "Genre",
        GenreRequest(nameEN = "Action", nameIT = "Azione", id = 4).toJson() to "Genre",
        GenreRequest(nameEN = "Comedy", nameIT = "Commedia", id = 12).toJson() to "Genre",
        GenreRequest(nameEN = "Crime", nameIT = "Crime", id = 2).toJson() to "Genre",
        GenreRequest(nameEN = "Documentary", nameIT = "Documentario", id = 24).toJson() to "Genre",
        GenreRequest(nameEN = "Drama", nameIT = "Dramma", id = 1).toJson() to "Genre",
        GenreRequest(nameEN = "Family", nameIT = "Famiglia", id = 16).toJson() to "Genre",
        GenreRequest(
            nameEN = "Science Fiction",
            nameIT = "Fantascienza",
            id = 10
        ).toJson() to "Genre",
        GenreRequest(nameEN = "Fantasy", nameIT = "Fantasy", id = 8).toJson() to "Genre",
        GenreRequest(nameEN = "Horror", nameIT = "Horror", id = 7).toJson() to "Genre",
        GenreRequest(nameEN = "Reality", nameIT = "Reality", id = 18).toJson() to "Genre",
        GenreRequest(nameEN = "Romance", nameIT = "Romance", id = 15).toJson() to "Genre",
        GenreRequest(nameEN = "Thriller", nameIT = "Thriller", id = 5).toJson() to "Genre",
        ArchiveRequest(type = "movie").toJson() to "Archive",
        ArchiveRequest(type = "tv").toJson() to "Archive",
    )

    private fun isHtmlPayload(payload: String): Boolean {
        val trimmed = payload.trimStart()
        return trimmed.startsWith("<") || trimmed.contains("<!DOCTYPE", ignoreCase = true)
    }

    private fun extractInertiaPageJson(html: String): String? {
        return StreamingCommunityPayloadParser.extractPageJson(html)
    }

    private fun parseInertiaPayload(payload: String, logContext: String): InertiaResponse? {
        if (payload.isBlank()) {
            Log.e(TAG, "$logContext: empty payload")
            return null
        }
        val json = StreamingCommunityPayloadParser.parseObject(payload)
        if (json == null) {
            Log.e(TAG, "$logContext: unable to extract JSON/Inertia payload")
            return null
        }
        return runCatching { parseJson<InertiaResponse>(json.toString()) }
            .onFailure { Log.e(TAG, "$logContext: invalid JSON payload - ${it.message}") }
            .getOrNull()
    }

    private fun parseBrowseTitles(payload: String, logContext: String): List<Title> {
        val jsonPayload = if (isHtmlPayload(payload)) {
            Log.e(TAG, "$logContext: received HTML payload, attempting embedded data-page fallback")
            extractInertiaPageJson(payload) ?: return emptyList()
        } else {
            payload
        }

        val result = parseInertiaPayload(jsonPayload, logContext) ?: return emptyList()
        return result.props.titles ?: emptyList()
    }

    private fun parseSliderFetchSections(payload: String): HomePageList? {
        if (payload.isBlank()) return null
        val trimmedPayload = payload.trimStart()
        if (trimmedPayload.startsWith("{") || trimmedPayload.contains("\"message\"")) {
            Log.e(
                TAG,
                "Sliders fetch: received error object instead of slider array: ${payload.take(300)}"
            )
            return null
        }
        if (isHtmlPayload(payload)) {
            Log.e(TAG, "Sliders fetch: expected JSON array but received HTML payload")
            return null
        }

        val slider = runCatching { parseJson<List<Slider>>(payload) }
            .onFailure {
                Log.e(TAG, "Sliders fetch: invalid JSON payload - ${it.message}")
                Log.e(TAG, payload)
            }
            .getOrNull()?.first()
            ?: return null
        val items = searchResponseBuilder(slider.titles)
        if (items.isEmpty()) return null
        return HomePageList(
            name = slider.label.ifBlank { slider.name },
            list = items,
            isHorizontalImages = false
        )
    }

    private suspend fun setupHeaders() {
        val response = app.get("$mainUrl/archive")
        val cookieJar = linkedMapOf<String, String>()
        response.cookies.forEach { cookieJar[it.key] = it.value }

        val csrfResponse = app.get(
            "${siteRootUrl}sanctum/csrf-cookie",
            headers = mapOf(
                "Referer" to "$mainUrl/",
                "X-Requested-With" to "XMLHttpRequest"
            )
        )
        csrfResponse.cookies.forEach { cookieJar[it.key] = it.value }

        headers["Cookie"] = cookieJar.entries.joinToString("; ") { "${it.key}=${it.value}" }
        decodedXsrfToken = cookieJar["XSRF-TOKEN"]
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            ?: ""

        val page = response.document
        val inertiaPageObject = page.select("#app").attr("data-page")
        inertiaVersion = inertiaPageObject
            .substringAfter("\"version\":\"")
            .substringBefore("\"")
        headers["X-Inertia-Version"] = inertiaVersion
    }

    private fun getSliderFetchHeaders(): Map<String, String> {
        return mapOf(
            "Cookie" to (headers["Cookie"] ?: ""),
            "X-Requested-With" to "XMLHttpRequest",
            "X-XSRF-TOKEN" to decodedXsrfToken,
            "Referer" to "$mainUrl/",
            "Accept" to "application/json, text/plain, */*",
            "Content-Type" to "application/json",
            "Origin" to siteRootUrl.removeSuffix("/")
        )
    }

    private fun searchResponseBuilder(listJson: List<Title>): List<SearchResponse> {
        val list: List<SearchResponse> =
            listJson.filter { it.type == "movie" || it.type == "tv" }.map { title ->
                val resolvedName = StreamingCommunityTitleResolver.resolve(title.name, title.slug, title.translations)
                val url = "$mainUrl/titles/${title.id}-${title.slug}"

                if (title.type == "tv") {
                    newTvSeriesSearchResponse(resolvedName, url) {
                        posterUrl = imageUrl(title.getPoster())
                    }
                } else {
                    newMovieSearchResponse(resolvedName, url) {
                        posterUrl = imageUrl(title.getPoster())
                    }
                }
            }
        return list
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        ensureRemoteDomain()
        if (!showUpcoming &&
            request.data == SliderFetchRequestSlider(
                name = "upcoming",
                genre = null
            ).toJson()
        ) {
            return null
        }

        if (headers["Cookie"].isNullOrEmpty()) {
            setupHeaders()
        }

        val hasNext = page < 17
        when (request.name) {
            "Slider" -> {
                val slider = parseJson<SliderFetchRequestSlider>(request.data)
                val body = "{\"sliders\":[${slider.toJson()}]}"
                val response = app.post(
                    "${siteRootUrl}api/sliders/fetch?lang=$lang",
                    headers = getSliderFetchHeaders(),
                    requestBody = body.toRequestBody()
                )
                val payload = response.body.string()
                val r = parseSliderFetchSections(payload) ?: return null
                return newHomePageResponse(r, hasNext = false)
            }
            "Genre" -> {
                val genre = parseJson<GenreRequest>(request.data)
                val response = app.get(
                    "${siteRootUrl}$lang/archive",
                    params = mapOf(
                        "page" to page.toString(),
                        "lang" to lang,
                        "genre[]" to genre.id.toString()
                    ),
                    headers = getSliderFetchHeaders(),
                )
                val payload = response.body.string()
                val data =
                    tryParseJson<it.dogior.hadEnough.SearchResponse>(payload)?.data ?: return null
                val name = if (lang == "en") genre.nameEN else genre.nameIT
                return newHomePageResponse(
                    HomePageList(
                        name = name,
                        list = searchResponseBuilder(data)
                    ), hasNext = hasNext
                )
            }
            "Archive" -> {
                val archive = parseJson<ArchiveRequest>(request.data)
                if (headers["Cookie"].isNullOrEmpty()) setupHeaders()
                val params = buildList {
                    archive.type?.let { add("type=$it") }
                    add("sort=${archive.sort}")
                    archive.genreId?.let { add("genre%5B%5D=$it") }
                    archive.year?.let { add("year=$it") }
                    archive.score?.let { add("score=$it") }
                    archive.views?.let { add("views=$it") }
                    archive.service?.let { add("service=$it") }
                    archive.quality?.let { add("quality=$it") }
                    archive.age?.let { add("age=$it") }
                    if (page > 1) add("page=$page")
                }.joinToString("&")
                val payload = app.get("$mainUrl/archive?$params", headers = getSliderFetchHeaders()).body.string()
                val data = parseBrowseTitles(payload, "Archive page=$page")
                return newHomePageResponse(HomePageList("Archivio", searchResponseBuilder(data)), hasNext = data.size >= 60)
            }
            else -> {
                return null
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureRemoteDomain()
        val url = "$mainUrl/search"
        val response = app.get(url, params = mapOf("q" to query)).body.string()
        val titles = parseBrowseTitles(response, "Search")
        return searchResponseBuilder(titles)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        ensureRemoteDomain()
        val params = mutableMapOf("q" to query)
        if (page > 1) params["page"] = page.toString()
        val response = app.get("$mainUrl/search", params = params).body.string()
        val titles = parseBrowseTitles(response, "Search page=$page")
        val items = searchResponseBuilder(titles)
        val hasNext = items.isNotEmpty() && items.size >= 60
        return newSearchResponseList(items, hasNext = hasNext)
    }

    private fun imageUrl(value: String?): String? {
        return StreamingCommunityImageResolver.resolve(value, cdnBaseUrl)
    }

    private suspend fun getPoster(title: TitleProp): String? {
        if (title.tmdbId != null) {
            val tmdbUrl = "https://www.themoviedb.org/${title.type}/${title.tmdbId}"
            val resp = app.get(tmdbUrl).document
            val img = resp.select("img.poster.w-full").attr("srcset").split(", ").last()
            return img
        } else {
            return imageUrl(title.getBackgroundImageId())
        }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureRemoteDomain()
        val actualUrl = getActualUrl(rebaseProviderUrl(url))
        if (headers["Cookie"].isNullOrEmpty()) {
            setupHeaders()
        }
        val response = app.get(actualUrl, headers = headers)
        val responseBody = response.body.string()

        val props = parseInertiaPayload(responseBody, "Load")?.props
            ?: error("StreamingCommunity: payload dettaglio non valido")
        cdnBaseUrl = StreamingCommunityCdnResolver.resolve(props.cdnUrl ?: props.cdnUrlCamel ?: props.cdn, siteRootUrl)
        val title = props.title!!
        val resolvedTitleName = StreamingCommunityTitleResolver.resolve(title.name, title.slug, title.translations)
        val initialComingSoon = StreamingCommunityAvailabilityResolver.isUpcoming(title.status, title.releaseDate)
        val hasPlayableMovie = if (
            title.type == "movie" &&
            initialComingSoon &&
            StreamingCommunityAvailabilityResolver.shouldProbeInconsistent(title.status, title.releaseDate)
        ) {
            probeMovieAvailability(title.id)
        } else {
            false
        }
        val comingSoon = StreamingCommunityAvailabilityResolver.shouldKeepUpcoming(
            title.status,
            title.releaseDate,
            hasPlayableMovie,
        )
        val genres = title.genres.map { it.name.capitalize() }
        val year = title.releaseDate?.substringBefore('-')?.toIntOrNull()
        val related = props.sliders?.getOrNull(0)
        val trailers = title.trailers?.mapNotNull { it.getYoutubeUrl() }
        val poster = getPoster(title)
        val logo = imageUrl(title.getLogoImageId())

        if (title.type == "tv") {
            val episodes: List<Episode> = getEpisodes(props)

            val tvShow = newTvSeriesLoadResponse(
                resolvedTitleName,
                actualUrl,
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = poster
                this.logoUrl = logo.takeIf { trailers.isNullOrEmpty() }
                title.getBackgroundImageId()
                    .let { this.backgroundPosterUrl = imageUrl(it) }

                this.tags = genres
                this.comingSoon = comingSoon
                this.episodes = episodes
                this.year = year
                this.plot = title.plot
                title.age?.let { this.contentRating = "$it+" }
                this.recommendations = related?.titles?.let { searchResponseBuilder(it) }
                title.imdbId?.let { this.addImdbId(it) }
                title.tmdbId?.let { this.addTMDbId(it.toString()) }
                this.addActors(title.mainActors?.map { it.name })
                this.addScore(title.score)
                if (trailers != null) {
                    if (trailers.isNotEmpty()) {
                        addTrailer(trailers)
                    }
                }

            }
            return tvShow
        } else {
            val data = LoadData(
                StreamingCommunityPlaybackUrlBuilder.movie(mainUrl, title.id),
                "movie",
                title.tmdbId
            )
            val movie = newMovieLoadResponse(
                resolvedTitleName,
                actualUrl,
                TvType.Movie,
                dataUrl = data.toJson()
            ) {
                this.posterUrl = poster
                this.logoUrl = logo.takeIf { trailers.isNullOrEmpty() }
                title.getBackgroundImageId()
                    .let { this.backgroundPosterUrl = imageUrl(it) }

                this.tags = genres
                this.comingSoon = comingSoon
                this.year = year
                this.plot = title.plot
                title.age?.let { this.contentRating = "$it+" }
                this.recommendations = related?.titles?.let { searchResponseBuilder(it) }
                this.addActors(title.mainActors?.map { it.name })
                this.addScore(title.score)

                title.imdbId?.let { this.addImdbId(it) }
                title.tmdbId?.let { this.addTMDbId(it.toString()) }

                title.runtime?.let { this.duration = it }
                if (trailers != null) {
                    if (trailers.isNotEmpty()) {
                        addTrailer(trailers)
                    }
                }
            }
            return movie
        }
    }

    private suspend fun probeMovieAvailability(titleId: Int): Boolean {
        return runCatching {
            val watchHtml = app.get("${siteRootUrl}${lang}/watch/$titleId", headers = headers).body.string()
            val watchDocument = org.jsoup.Jsoup.parse(watchHtml)
            val rawIframe = watchDocument.selectFirst("iframe[src]")?.attr("src")
                ?: Regex("https?://[^\"'\\s]+vixcloud\\.co/(?:embed|playlist)/\\d+[^\"'\\s]*", RegexOption.IGNORE_CASE)
                    .find(watchHtml)?.value
                ?: return@runCatching false
            val iframeUrl = if (rawIframe.startsWith("http")) rawIframe else "$siteRootUrl${rawIframe.trimStart('/')}"
            val iframeHtml = app.get(iframeUrl, headers = headers).body.string()
            Regex("https?://[^\"'\\s]+vixcloud\\.co/(?:embed|playlist)/\\d+[^\"'\\s]*", RegexOption.IGNORE_CASE)
                .containsMatchIn(iframeHtml)
        }.getOrDefault(false)
    }

    private fun getActualUrl(url: String) =
        if (!url.contains(mainUrl)) {
            val replacingValue =
                if (url.contains("/it/") || url.contains("/en/")) mainUrl.toHttpUrl().host else mainUrl.toHttpUrl().host + "/$lang"
            val actualUrl = url.replace(url.toHttpUrl().host, replacingValue)

//            Log.d("$TAG:UrlFix", "Old: $url\nNew: $actualUrl")
            actualUrl
        } else {
            url
        }

    private suspend fun getEpisodes(props: Props): List<Episode> {
        val episodeList = mutableListOf<Episode>()
        val title = props.title

        title?.seasons?.forEach { season ->
            val responseEpisodes = emptyList<it.dogior.hadEnough.Episode>().toMutableList()
            if (season.id == props.loadedSeason!!.id) {
                responseEpisodes.addAll(props.loadedSeason.episodes!!)
            } else {
                if (inertiaVersion == "") {
                    setupHeaders()
                }
                val url = "$mainUrl/titles/${title.id}-${title.slug}/season-${season.number}"
                val seasonPayload = parseInertiaPayload(
                    app.get(url, headers = headers).body.string(),
                    "Season ${season.number}",
                )
                responseEpisodes.addAll(seasonPayload?.props?.loadedSeason?.episodes.orEmpty())
            }
            responseEpisodes.forEach { ep ->

                val loadData = LoadData(
                    StreamingCommunityPlaybackUrlBuilder.episode(mainUrl, title.id, ep.id),
                    type = "tv",
                    tmdbId = title.tmdbId,
                    seasonNumber = season.number,
                    episodeNumber = ep.number
                )
                episodeList.add(
                    newEpisode(loadData.toJson()) {
                        this.name = StreamingCommunityEpisodeResolver.resolve(ep.name, ep.number, ep.translations)
                        this.posterUrl = imageUrl(ep.getCover())
                        this.description = ep.plot
                        this.episode = ep.number
                        this.season = season.number
                        this.runTime = ep.duration
                    }
                )
            }
        }

        return episodeList
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureRemoteDomain()
//        Log.d(TAG, "Load Data : $data")
        if (data.isEmpty()) return false
        val loadData = parseJson<LoadData>(data)

        val currentLoadUrl = rebaseProviderUrl(loadData.url)
        val responseBody = app.get(currentLoadUrl).body.string()
        val iframeSrc = StreamingCommunityEmbedResolver.resolveIframeUrl(responseBody, currentLoadUrl)
            ?: StreamingCommunityEmbedResolver.resolveEmbedUrl(responseBody, currentLoadUrl)?.let { embedUrl ->
                StreamingCommunityEmbedResolver.resolveIframeUrl(app.get(embedUrl).body.string(), embedUrl)
            }
            ?: return false

        VixCloudExtractor().getUrl(
            url = iframeSrc,
            referer = siteRootUrl,
            subtitleCallback = subtitleCallback,
            callback = callback
        )

        val vixsrcUrl = if (loadData.type == "movie") {
            "https://vixsrc.to/movie/${loadData.tmdbId}"
        } else {
            "https://vixsrc.to/tv/${loadData.tmdbId}/${loadData.seasonNumber}/${loadData.episodeNumber}"
        }

        VixSrcExtractor().getUrl(
            url = vixsrcUrl,
            referer = "https://vixsrc.to/",
            subtitleCallback = subtitleCallback,
            callback = callback
        )

        return true
    }
}
