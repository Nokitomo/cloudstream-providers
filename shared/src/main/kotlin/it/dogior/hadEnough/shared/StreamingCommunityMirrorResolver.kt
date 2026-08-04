package it.dogior.hadEnough.shared

import android.content.SharedPreferences
import com.lagradost.cloudstream3.app
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class StreamingCommunityMirrorCandidate(
    val site: PastebinSite,
    val url: String,
)

object StreamingCommunityMirrorResolver {
    private const val CANDIDATES_PREFIX = "streamingMirrorCandidates_"
    private const val EFFECTIVE_PREFIX = "streamingMirrorEffective_"
    private const val CHECKED_AT_PREFIX = "streamingMirrorCheckedAt_"
    private const val SUCCESS_TTL_MS = 5L * 60L * 1000L
    private const val FAILURE_RETRY_MS = 60L * 1000L
    private const val REQUEST_TIMEOUT_SECONDS = 15L

    private data class CachedMirror(
        val candidatesKey: String,
        val effectiveUrl: String,
        val checkedAt: Long,
        val successful: Boolean,
    )

    private val memoryCache = ConcurrentHashMap<String, CachedMirror>()

    internal data class ProbeResponse(
        val code: Int,
        val url: String,
        val body: String,
    )

    suspend fun resolve(
        preferences: SharedPreferences?,
        cacheKey: String,
        candidates: List<StreamingCommunityMirrorCandidate>,
        now: Long = System.currentTimeMillis(),
    ): String {
        return resolveWithProbe(preferences, cacheKey, candidates, now) { url ->
            val response = app.get(
                url,
                allowRedirects = true,
                timeout = REQUEST_TIMEOUT_SECONDS,
            )
            ProbeResponse(response.code, response.url, response.text)
        }
    }

    internal suspend fun resolveWithProbe(
        preferences: SharedPreferences?,
        cacheKey: String,
        candidates: List<StreamingCommunityMirrorCandidate>,
        now: Long = System.currentTimeMillis(),
        probe: suspend (String) -> ProbeResponse,
    ): String {
        val normalizedCandidates = candidates.mapNotNull { candidate ->
            PastebinDomainRegistry.normalizeForSite(candidate.url, candidate.site)?.let {
                candidate.copy(url = it)
            }
        }.distinctBy { it.url }
        if (normalizedCandidates.isEmpty()) return candidates.firstOrNull()?.url.orEmpty()

        val candidatesKey = normalizedCandidates.joinToString("|") { "${it.site.preferenceKey}:${it.url}" }
        val cached = memoryCache[cacheKey] ?: readPersisted(preferences, cacheKey, normalizedCandidates)
        if (cached != null && cached.candidatesKey == candidatesKey) {
            val ttl = if (cached.successful) SUCCESS_TTL_MS else FAILURE_RETRY_MS
            if (now - cached.checkedAt in 0 until ttl) return cached.effectiveUrl
        }

        normalizedCandidates.forEach { candidate ->
            val response = runCatching { probe("${candidate.url}/it/archive") }
                .getOrNull() ?: return@forEach
            if (response.code !in 200..299 || !isValidArchivePayload(response.body)) return@forEach
            val effectiveUrl = PastebinDomainRegistry.normalizeResponseOriginForSite(
                response.url,
                candidate.site,
            ) ?: return@forEach
            val resolved = CachedMirror(candidatesKey, effectiveUrl, now, successful = true)
            memoryCache[cacheKey] = resolved
            persist(preferences, cacheKey, resolved)
            PastebinDomainResolver.rememberWorkingUrl(preferences, candidate.site, effectiveUrl)
            return effectiveUrl
        }

        val fallback = normalizedCandidates.first().url
        val failed = CachedMirror(candidatesKey, fallback, now, successful = false)
        memoryCache[cacheKey] = failed
        persist(preferences, cacheKey, failed)
        return fallback
    }

    fun isValidArchivePayload(payload: String): Boolean {
        val text = payload.trim()
        if (text.isBlank()) return false
        val jsonText = if (text.startsWith("{")) {
            text
        } else {
            val raw = Jsoup.parse(text)
                .select("#app[data-page], [data-page]")
                .firstOrNull()
                ?.attr("data-page")
                ?.takeIf(String::isNotBlank)
                ?: return false
            Parser.unescapeEntities(Parser.unescapeEntities(raw, true), true)
        }
        return runCatching {
            val page = JSONObject(jsonText)
            page.optString("component") == "Titles/Browse" &&
                page.optJSONObject("props")?.has("titles") == true
        }.getOrDefault(false)
    }

    internal fun clearMemoryCache() {
        memoryCache.clear()
    }

    private fun readPersisted(
        preferences: SharedPreferences?,
        cacheKey: String,
        candidates: List<StreamingCommunityMirrorCandidate>,
    ): CachedMirror? {
        val candidatesKey = preferences?.getString(candidatesPreferenceKey(cacheKey), null) ?: return null
        val effective = preferences.getString(effectivePreferenceKey(cacheKey), null) ?: return null
        val validEffective = candidates.firstNotNullOfOrNull { candidate ->
            PastebinDomainRegistry.normalizeResponseOriginForSite(effective, candidate.site)
        } ?: return null
        return CachedMirror(
            candidatesKey = candidatesKey,
            effectiveUrl = validEffective,
            checkedAt = preferences.getLong(checkedAtPreferenceKey(cacheKey), 0L),
            successful = preferences.getBoolean(successfulPreferenceKey(cacheKey), false),
        ).also { memoryCache[cacheKey] = it }
    }

    private fun persist(
        preferences: SharedPreferences?,
        cacheKey: String,
        mirror: CachedMirror,
    ) {
        preferences?.edit()
            ?.putString(candidatesPreferenceKey(cacheKey), mirror.candidatesKey)
            ?.putString(effectivePreferenceKey(cacheKey), mirror.effectiveUrl)
            ?.putLong(checkedAtPreferenceKey(cacheKey), mirror.checkedAt)
            ?.putBoolean(successfulPreferenceKey(cacheKey), mirror.successful)
            ?.apply()
    }

    private fun candidatesPreferenceKey(cacheKey: String) = CANDIDATES_PREFIX + cacheKey
    private fun effectivePreferenceKey(cacheKey: String) = EFFECTIVE_PREFIX + cacheKey
    private fun checkedAtPreferenceKey(cacheKey: String) = CHECKED_AT_PREFIX + cacheKey
    private fun successfulPreferenceKey(cacheKey: String) = "streamingMirrorSuccessful_" + cacheKey
}
