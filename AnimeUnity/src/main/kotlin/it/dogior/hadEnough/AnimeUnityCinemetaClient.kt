package it.dogior.hadEnough

import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

internal object AnimeUnityCinemetaClient {
    private data class Cached(val value: String?, val expiresAt: Long)
    private val cache = ConcurrentHashMap<String, Cached>()
    private const val SUCCESS_TTL = 12L * 60L * 60L * 1000L
    private const val MISS_TTL = 60L * 60L * 1000L

    suspend fun resolve(titleCandidates: List<String>, year: Int?, isMovie: Boolean): String? {
        val candidates = titleCandidates.map(String::trim).filter(String::isNotBlank).distinct().take(3)
        val key = "${if (isMovie) "movie" else "series"}:${candidates.joinToString("|").lowercase(Locale.ROOT)}:$year"
        cache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.value }
        val type = if (isMovie) "movie" else "series"
        val result = candidates.firstNotNullOfOrNull { title ->
            val encoded = URLEncoder.encode(title, StandardCharsets.UTF_8.name()).replace("+", "%20")
            val response = try {
                app.get("https://v3-cinemeta.strem.io/catalog/$type/top/search=$encoded.json", timeout = 10L)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                return@firstNotNullOfOrNull null
            }
            if (response.code !in 200..299) return@firstNotNullOfOrNull null
            selectImdbId(runCatching { JSONObject(response.text).optJSONArray("metas") }.getOrNull(), title, year, type)
        }
        cache[key] = Cached(result, System.currentTimeMillis() + if (result == null) MISS_TTL else SUCCESS_TTL)
        return result
    }

    internal fun selectImdbId(metas: JSONArray?, query: String, year: Int?, type: String): String? {
        val normalizedQuery = normalize(query)
        val matches = buildList {
            for (index in 0 until (metas?.length() ?: 0)) {
                val meta = metas?.optJSONObject(index) ?: continue
                val id = meta.optString("id").takeIf { it.matches(Regex("tt\\d+")) } ?: continue
                if (!meta.optString("type").equals(type, ignoreCase = true)) continue
                if (normalize(meta.optString("name")) != normalizedQuery) continue
                add(id to meta.optString("releaseInfo"))
            }
        }
        return matches.firstOrNull { year != null && it.second.startsWith(year.toString()) }?.first
            ?: matches.singleOrNull()?.first
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
}
