package it.dogior.hadEnough.shared

import java.net.URI
import java.util.Locale

enum class PastebinSite(
    val preferenceKey: String,
    val fallbackUrl: String,
    internal val hostMatchers: List<Regex>,
) {
    ANIME_UNITY(
        preferenceKey = "animeunity",
        fallbackUrl = "https://www.animeunity.so",
        hostMatchers = listOf(Regex("^(?:www\\.)?animeunity\\.[a-z0-9-]+$")),
    ),
    ANIME_WORLD(
        preferenceKey = "animeworld",
        fallbackUrl = "https://www.animeworld.ac",
        hostMatchers = listOf(Regex("^(?:www\\.)?animeworld\\.[a-z0-9-]+$")),
    ),
    ANIME_SATURN(
        preferenceKey = "animesaturn",
        fallbackUrl = "https://www.animesaturn.net",
        hostMatchers = listOf(Regex("^(?:www\\.)?animesaturn\\.[a-z0-9-]+$")),
    ),
    STREAMING_UNITY(
        preferenceKey = "streamingunity",
        fallbackUrl = "https://streamingunity.cc",
        hostMatchers = listOf(Regex("^(?:www\\.)?streamingunity\\.[a-z0-9-]+$")),
    ),
    STREAMING_COMMUNITY(
        preferenceKey = "streamingcommunity",
        fallbackUrl = "https://streamingcommunityz.support",
        hostMatchers = listOf(
            Regex("^(?:www\\.)?streamingcommunityz\\.[a-z0-9-]+$"),
            Regex("^(?:www\\.)?streaming-community\\.[a-z0-9-]+$"),
            Regex("^(?:www\\.)?streamingunity\\.[a-z0-9-]+$"),
        ),
    ),
    ALTA_DEFINIZIONE(
        preferenceKey = "altadefinizione",
        fallbackUrl = "https://altadefinizione.autos",
        hostMatchers = listOf(
            Regex("^(?:www\\.)?altadefinizione(?:z|gratis)?\\.[a-z0-9-]+$"),
        ),
    ),
    CB01(
        preferenceKey = "cb01",
        fallbackUrl = "https://cb01uno.uno",
        hostMatchers = listOf(Regex("^(?:www\\.)?cb01uno\\.[a-z0-9-]+$")),
    ),
    GUARDA_SERIE_TV(
        preferenceKey = "guardaserietv",
        fallbackUrl = "https://guardaserietv.biz",
        hostMatchers = listOf(Regex("^(?:www\\.)?guardaserietv\\.[a-z0-9-]+$")),
    ),
    EURO_STREAMING(
        preferenceKey = "eurostreaming",
        fallbackUrl = "https://eurostreaming.ovh",
        hostMatchers = listOf(
            Regex("^(?:www\\.)?eurostreaming\\.[a-z0-9-]+$"),
            Regex("^(?:www\\.)?eurostreamings\\.[a-z0-9-]+$"),
        ),
    ),
}

object PastebinDomainRegistry {
    private const val MAX_PAYLOAD_LENGTH = 16 * 1024
    private const val MAX_LINES = 128
    private val ipAddress = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")

    fun resolve(text: String, site: PastebinSite): String? {
        if (text.length > MAX_PAYLOAD_LENGTH) return null

        val entries = text.lineSequence()
            .take(MAX_LINES + 1)
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
        if (entries.size > MAX_LINES) return null

        val namedCandidates = entries.mapNotNull { parseNamedEntry(it) }
            .filter { it.first.equals(site.preferenceKey, ignoreCase = true) }
            .map { it.second }
        val candidates = namedCandidates + entries.filterNot { parseNamedEntry(it) != null }

        val normalizedCandidates = candidates.mapNotNull(::normalizeRemoteCandidate)
        return site.hostMatchers.firstNotNullOfOrNull { matcher ->
            normalizedCandidates.firstOrNull { normalized ->
                matcher.matches(URI(normalized).host.lowercase(Locale.ROOT))
            }
        }
    }

    fun normalizeFallback(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        val withScheme = if ("://" in raw) raw else "https://$raw"
        return normalizeHttpsOrigin(withScheme, requirePublicHost = false)
    }

    fun rebaseUrl(value: String, site: PastebinSite, newBaseUrl: String): String {
        return runCatching {
            val original = URI(value)
            val host = original.host?.lowercase(Locale.ROOT) ?: return value
            if (site.hostMatchers.none { it.matches(host) }) return value
            val base = URI(normalizeFallback(newBaseUrl) ?: return value)
            URI(
                base.scheme,
                null,
                base.host,
                base.port,
                original.rawPath.ifBlank { "/" },
                original.rawQuery,
                original.rawFragment,
            ).toString()
        }.getOrDefault(value)
    }

    fun normalizeForSite(value: String?, site: PastebinSite): String? {
        val normalized = value?.let { normalizeHttpsOrigin(it, requirePublicHost = true) } ?: return null
        val host = URI(normalized).host.lowercase(Locale.ROOT)
        return normalized.takeIf { site.hostMatchers.any { matcher -> matcher.matches(host) } }
    }

    fun resolveRedirectOrigin(
        requestedUrl: String?,
        responseUrl: String?,
        site: PastebinSite,
    ): String? {
        val requestedOrigin = normalizeFallback(requestedUrl) ?: return null
        val responseOrigin = normalizeHttpsResponseOrigin(responseUrl) ?: return null
        val responseHost = URI(responseOrigin).host.lowercase(Locale.ROOT)
        if (site.hostMatchers.none { matcher -> matcher.matches(responseHost) }) return null
        return responseOrigin.takeUnless { it.equals(requestedOrigin, ignoreCase = true) }
    }

    private fun parseNamedEntry(line: String): Pair<String, String>? {
        if (line.startsWith("http://", true) || line.startsWith("https://", true)) return null
        val separator = line.indexOf('=')
        if (separator <= 0 || separator == line.lastIndex) return null
        val key = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim()
        if (!key.matches(Regex("[a-zA-Z0-9_-]+"))) return null
        return key to value
    }

    private fun normalizeRemoteCandidate(value: String): String? {
        return normalizeHttpsOrigin(value, requirePublicHost = true)
    }

    private fun normalizeHttpsOrigin(value: String, requirePublicHost: Boolean): String? {
        return runCatching {
            val uri = URI(value.trim())
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (!uri.userInfo.isNullOrBlank()) return null
            if (uri.port !in listOf(-1, 443)) return null
            if (!uri.rawQuery.isNullOrBlank() || !uri.rawFragment.isNullOrBlank()) return null
            if (uri.path !in listOf("", "/")) return null

            val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
            if (host.isBlank() || host == "localhost") return null
            if (requirePublicHost && (ipAddress.matches(host) || host.endsWith(".local"))) return null

            "https://$host"
        }.getOrNull()
    }

    private fun normalizeHttpsResponseOrigin(value: String?): String? {
        return runCatching {
            val uri = URI(value?.trim().orEmpty())
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.rawUserInfo != null || uri.port != -1) return null

            val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
            if (host.isBlank() || host == "localhost") return null
            if (ipAddress.matches(host) || host.endsWith(".local")) return null

            "https://$host"
        }.getOrNull()
    }
}
