package it.dogior.hadEnough.shared

import android.content.SharedPreferences
import com.lagradost.cloudstream3.app
import java.util.concurrent.ConcurrentHashMap

object ProviderRedirectResolver {
    private const val CANDIDATE_PREFIX = "providerRedirectCandidate_"
    private const val EFFECTIVE_PREFIX = "providerRedirectEffective_"
    private const val CHECKED_AT_PREFIX = "providerRedirectCheckedAt_"
    private const val SUCCESS_TTL_MS = 6L * 60L * 60L * 1000L
    private const val FAILURE_RETRY_MS = 5L * 60L * 1000L
    private const val REQUEST_TIMEOUT_SECONDS = 15L

    private data class CachedResolution(
        val candidate: String,
        val effective: String,
        val checkedAt: Long,
        val successful: Boolean,
    )

    private val memoryCache = ConcurrentHashMap<String, CachedResolution>()

    suspend fun resolve(
        preferences: SharedPreferences?,
        site: PastebinSite,
        initialUrl: String,
        requestHeaders: Map<String, String> = emptyMap(),
        now: Long = System.currentTimeMillis(),
    ): String {
        val candidate = PastebinDomainRegistry.normalizeFallback(initialUrl) ?: return initialUrl
        val cacheKey = site.preferenceKey
        val cached = memoryCache[cacheKey] ?: readPersisted(preferences, site)
        if (cached != null && cached.candidate == candidate) {
            val ttl = if (cached.successful) SUCCESS_TTL_MS else FAILURE_RETRY_MS
            if (now - cached.checkedAt in 0 until ttl) return cached.effective
        }

        val response = runCatching {
            app.get(
                candidate,
                headers = requestHeaders,
                allowRedirects = true,
                timeout = REQUEST_TIMEOUT_SECONDS,
            )
        }.getOrNull()
        val successful = response?.code in 200..299
        val redirected = if (successful) {
            PastebinDomainRegistry.resolveRedirectOrigin(candidate, response?.url, site)
        } else {
            null
        }
        val effective = redirected ?: candidate
        val resolution = CachedResolution(candidate, effective, now, successful)
        memoryCache[cacheKey] = resolution
        persist(preferences, site, resolution)
        if (redirected != null) {
            PastebinDomainResolver.rememberWorkingUrl(preferences, site, redirected)
        }
        return effective
    }

    internal fun clearMemoryCache() {
        memoryCache.clear()
    }

    private fun readPersisted(
        preferences: SharedPreferences?,
        site: PastebinSite,
    ): CachedResolution? {
        val candidate = preferences?.getString(candidateKey(site), null) ?: return null
        val effective = preferences.getString(effectiveKey(site), null) ?: return null
        val checkedAt = preferences.getLong(checkedAtKey(site), 0L)
        val successful = preferences.getBoolean(successfulKey(site), false)
        val normalizedCandidate = PastebinDomainRegistry.normalizeFallback(candidate) ?: return null
        val normalizedEffective = if (effective.equals(normalizedCandidate, ignoreCase = true)) {
            normalizedCandidate
        } else {
            PastebinDomainRegistry.resolveRedirectOrigin(normalizedCandidate, effective, site)
                ?: return null
        }
        return CachedResolution(normalizedCandidate, normalizedEffective, checkedAt, successful)
            .also { memoryCache[site.preferenceKey] = it }
    }

    private fun persist(
        preferences: SharedPreferences?,
        site: PastebinSite,
        resolution: CachedResolution,
    ) {
        preferences?.edit()
            ?.putString(candidateKey(site), resolution.candidate)
            ?.putString(effectiveKey(site), resolution.effective)
            ?.putLong(checkedAtKey(site), resolution.checkedAt)
            ?.putBoolean(successfulKey(site), resolution.successful)
            ?.apply()
    }

    private fun candidateKey(site: PastebinSite) = CANDIDATE_PREFIX + site.preferenceKey
    private fun effectiveKey(site: PastebinSite) = EFFECTIVE_PREFIX + site.preferenceKey
    private fun checkedAtKey(site: PastebinSite) = CHECKED_AT_PREFIX + site.preferenceKey
    private fun successfulKey(site: PastebinSite) = "providerRedirectSuccessful_" + site.preferenceKey
}
