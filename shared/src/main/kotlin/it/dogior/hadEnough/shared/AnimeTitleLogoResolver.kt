package it.dogior.hadEnough.shared

import com.lagradost.cloudstream3.app
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

data class AnimeTitleArtwork(
    val imdbId: String? = null,
    val logoUrl: String? = null,
)

object AnimeTitleLogoResolver {
    private data class Cached<T>(val value: T, val expiresAt: Long)

    private val aniZipCache = ConcurrentHashMap<String, Cached<AnimeTitleArtwork>>()
    private val cinemetaCache = ConcurrentHashMap<String, Cached<String?>>()

    private const val ANIZIP_URL = "https://api.ani.zip/mappings"
    private const val CINEMETA_URL = "https://v3-cinemeta.strem.io/meta"
    private const val SUCCESS_TTL_MS = 12L * 60L * 60L * 1000L
    private const val MISS_TTL_MS = 60L * 60L * 1000L
    private const val MAX_RESPONSE_SIZE = 4 * 1024 * 1024
    private const val REQUEST_TIMEOUT_SECONDS = 4L
    private val IMDB_ID = Regex("tt\\d{5,}", RegexOption.IGNORE_CASE)

    suspend fun resolve(
        anilistId: Int?,
        malId: Int?,
        isMovie: Boolean,
        knownImdbId: String? = null,
        fallbackLogoUrl: String? = null,
    ): AnimeTitleArtwork {
        val normalizedKnownImdb = normalizeImdbId(knownImdbId)
        val aniZip = if (normalizedKnownImdb == null && (anilistId != null || malId != null)) {
            fetchAniZip(anilistId, malId)
        } else {
            AnimeTitleArtwork()
        }
        val imdbId = normalizedKnownImdb ?: aniZip.imdbId
        val cinemetaLogo = imdbId?.let { fetchCinemetaLogo(it, isMovie) }
        return AnimeTitleArtwork(
            imdbId = imdbId,
            logoUrl = cinemetaLogo ?: validHttpsUrl(fallbackLogoUrl) ?: aniZip.logoUrl,
        )
    }

    suspend fun resolveForImdb(
        imdbId: String?,
        isMovie: Boolean,
        fallbackLogoUrl: String? = null,
    ): AnimeTitleArtwork {
        return resolve(
            anilistId = null,
            malId = null,
            isMovie = isMovie,
            knownImdbId = imdbId,
            fallbackLogoUrl = fallbackLogoUrl,
        )
    }

    internal fun parseAniZipPayload(text: String): AnimeTitleArtwork {
        val root = parseRoot(text) ?: return AnimeTitleArtwork()
        val imdbId = normalizeImdbId(root.optJSONObject("mappings")?.optString("imdb_id"))
        val images = root.optJSONArray("images")
        var logoUrl: String? = null
        for (index in 0 until (images?.length() ?: 0)) {
            val image = images?.optJSONObject(index) ?: continue
            val coverType = image.optString("coverType").trim()
            if (!coverType.equals("Clearlogo", ignoreCase = true) &&
                !coverType.equals("Clear Logo", ignoreCase = true) &&
                !coverType.equals("Logo", ignoreCase = true)
            ) continue
            logoUrl = validHttpsUrl(image.optString("url"))
            if (logoUrl != null) break
        }
        return AnimeTitleArtwork(imdbId = imdbId, logoUrl = logoUrl)
    }

    internal fun parseCinemetaLogo(text: String): String? {
        val root = parseRoot(text) ?: return null
        return validHttpsUrl(root.optJSONObject("meta")?.optString("logo"))
    }

    private suspend fun fetchAniZip(anilistId: Int?, malId: Int?): AnimeTitleArtwork {
        val lookup = anilistId?.takeIf { it > 0 }?.let { "anilist_id=$it" }
            ?: malId?.takeIf { it > 0 }?.let { "mal_id=$it" }
            ?: return AnimeTitleArtwork()
        val now = System.currentTimeMillis()
        aniZipCache[lookup]?.takeIf { it.expiresAt > now }?.let { return it.value }
        val result = try {
            val response = app.get("$ANIZIP_URL?$lookup", timeout = REQUEST_TIMEOUT_SECONDS)
            if (response.code !in 200..299 || response.text.length > MAX_RESPONSE_SIZE) {
                AnimeTitleArtwork()
            } else {
                parseAniZipPayload(response.text)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            AnimeTitleArtwork()
        }
        val successful = result.imdbId != null || result.logoUrl != null
        aniZipCache[lookup] = Cached(
            result,
            now + if (successful) SUCCESS_TTL_MS else MISS_TTL_MS,
        )
        return result
    }

    private suspend fun fetchCinemetaLogo(imdbId: String, isMovie: Boolean): String? {
        val preferredType = if (isMovie) "movie" else "series"
        val preferred = fetchCinemetaLogo(imdbId, preferredType)
        if (preferred != null) return preferred
        val alternateType = if (isMovie) "series" else "movie"
        return fetchCinemetaLogo(imdbId, alternateType)
    }

    private suspend fun fetchCinemetaLogo(imdbId: String, type: String): String? {
        val key = "$type:$imdbId"
        val now = System.currentTimeMillis()
        cinemetaCache[key]?.takeIf { it.expiresAt > now }?.let { return it.value }
        val result = try {
            val response = app.get("$CINEMETA_URL/$type/$imdbId.json", timeout = REQUEST_TIMEOUT_SECONDS)
            if (response.code !in 200..299 || response.text.length > MAX_RESPONSE_SIZE) {
                null
            } else {
                parseCinemetaLogo(response.text)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        cinemetaCache[key] = Cached(
            result,
            now + if (result == null) MISS_TTL_MS else SUCCESS_TTL_MS,
        )
        return result
    }

    private fun parseRoot(text: String): JSONObject? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_RESPONSE_SIZE) return null
        return runCatching { JSONObject(trimmed) }.getOrNull()
    }

    private fun normalizeImdbId(value: String?): String? {
        return value
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(IMDB_ID::matches)
    }

    private fun validHttpsUrl(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.startsWith("https://", ignoreCase = true) && it.length <= 2_048 }
    }
}
