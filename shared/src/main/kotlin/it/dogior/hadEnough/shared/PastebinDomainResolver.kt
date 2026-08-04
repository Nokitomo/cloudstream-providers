package it.dogior.hadEnough.shared

import android.content.SharedPreferences
import com.lagradost.cloudstream3.app

object PastebinDomainResolver {
    const val PASTEBIN_URL = "https://pastebin.com/raw/KgQ4jTy6"
    const val PREFERENCES_NAME = "DogiorRemoteProviderDomains"

    private const val PAYLOAD_KEY = "pastebinDomainPayload"
    private const val PAYLOAD_UPDATED_AT_KEY = "pastebinDomainPayloadUpdatedAt"
    private const val LAST_ATTEMPT_AT_KEY = "pastebinDomainLastAttemptAt"
    private const val LAST_GOOD_PREFIX = "pastebinDomainLastGood_"
    private const val LAST_CANDIDATE_PREFIX = "pastebinDomainLastCandidate_"
    private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L
    private const val FAILURE_RETRY_MS = 5L * 60L * 1000L
    private const val REQUEST_TIMEOUT_SECONDS = 5L
    private const val MAX_PAYLOAD_LENGTH = 16 * 1024

    @Volatile
    private var memoryPayload: String? = null

    @Volatile
    private var memoryPayloadUpdatedAt: Long = 0L

    @Volatile
    private var memoryLastAttemptAt: Long = 0L

    suspend fun resolve(
        preferences: SharedPreferences?,
        site: PastebinSite,
        configuredFallback: String? = null,
        now: Long = System.currentTimeMillis(),
    ): String {
        val fallback = PastebinDomainRegistry.normalizeFallback(configuredFallback)
            ?: site.fallbackUrl
        val cachedPayload = currentPayload(preferences)
        val cachedAt = currentPayloadTimestamp(preferences)

        if (cachedPayload != null && now - cachedAt in 0 until CACHE_TTL_MS) {
            return resolveCandidate(preferences, site, cachedPayload)
                ?: lastGood(preferences, site)
                ?: fallback
        }

        val lastAttemptAt = memoryLastAttemptAt.takeIf { it > 0L }
            ?: preferences?.getLong(LAST_ATTEMPT_AT_KEY, 0L)
            ?: 0L
        if (now - lastAttemptAt in 0 until FAILURE_RETRY_MS) {
            return resolveCandidate(preferences, site, cachedPayload.orEmpty())
                ?: lastGood(preferences, site)
                ?: fallback
        }

        memoryLastAttemptAt = now
        preferences?.edit()?.putLong(LAST_ATTEMPT_AT_KEY, now)?.apply()

        val refreshedPayload = fetchPayload()
        if (refreshedPayload != null) {
            memoryPayload = refreshedPayload
            memoryPayloadUpdatedAt = now
            preferences?.edit()
                ?.putString(PAYLOAD_KEY, refreshedPayload)
                ?.putLong(PAYLOAD_UPDATED_AT_KEY, now)
                ?.apply()
        }

        return resolveCandidate(preferences, site, refreshedPayload ?: cachedPayload.orEmpty())
            ?: lastGood(preferences, site)
            ?: fallback
    }

    fun rememberWorkingUrl(
        preferences: SharedPreferences?,
        site: PastebinSite,
        url: String?,
    ) {
        val normalized = PastebinDomainRegistry.normalizeForSite(url, site) ?: return
        preferences?.edit()?.putString(lastGoodKey(site), normalized)?.apply()
    }

    internal fun clearMemoryCache() {
        memoryPayload = null
        memoryPayloadUpdatedAt = 0L
        memoryLastAttemptAt = 0L
    }

    private fun currentPayload(preferences: SharedPreferences?): String? {
        return memoryPayload ?: preferences?.getString(PAYLOAD_KEY, null)
    }

    private fun currentPayloadTimestamp(preferences: SharedPreferences?): Long {
        return memoryPayloadUpdatedAt.takeIf { it > 0L }
            ?: preferences?.getLong(PAYLOAD_UPDATED_AT_KEY, 0L)
            ?: 0L
    }

    private suspend fun fetchPayload(): String? {
        return runCatching {
            val response = app.get(PASTEBIN_URL, timeout = REQUEST_TIMEOUT_SECONDS)
            response.text.takeIf {
                response.code in 200..299 && it.isNotBlank() && it.length <= MAX_PAYLOAD_LENGTH
            }
        }.getOrNull()
    }

    private fun resolveCandidate(
        preferences: SharedPreferences?,
        site: PastebinSite,
        payload: String,
    ): String? {
        val candidate = PastebinDomainRegistry.resolve(payload, site) ?: return null
        val lastGood = lastGood(preferences, site)
        val lastCandidate = preferences?.getString(lastCandidateKey(site), null)
        if (candidate == lastCandidate && lastGood != null) return lastGood

        preferences?.edit()
            ?.putString(lastCandidateKey(site), candidate)
            ?.putString(lastGoodKey(site), candidate)
            ?.apply()
        return candidate
    }

    private fun lastGood(preferences: SharedPreferences?, site: PastebinSite): String? {
        return PastebinDomainRegistry.normalizeForSite(
            preferences?.getString(lastGoodKey(site), null),
            site,
        )
    }

    private fun lastGoodKey(site: PastebinSite): String = LAST_GOOD_PREFIX + site.preferenceKey
    private fun lastCandidateKey(site: PastebinSite): String = LAST_CANDIDATE_PREFIX + site.preferenceKey
}
