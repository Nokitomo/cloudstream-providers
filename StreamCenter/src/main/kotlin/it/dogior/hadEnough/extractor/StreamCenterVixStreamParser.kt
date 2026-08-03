package it.dogior.hadEnough.extractor

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray

internal data class StreamCenterVixStream(
    val url: String,
    val label: String,
    val isDownload: Boolean = false,
)

internal object StreamCenterVixStreamParser {
    private val originRegex = Regex("^(https?://[^/?#]+)", RegexOption.IGNORE_CASE)
    private val vixIdRegex = Regex("vixcloud\\.co/(?:embed|playlist)/(\\d+)", RegexOption.IGNORE_CASE)

    fun parse(html: String, embedUrl: String): List<StreamCenterVixStream> {
        val streams = extractStreams(html)
        val params = extractParams(html, embedUrl)
        val masterUrl = extractMasterUrl(html, embedUrl)
        val fhd = canPlayFhd(html, embedUrl)
        val output = mutableListOf<StreamCenterVixStream>()
        val candidates = streams.toMutableList()
        if (!masterUrl.isNullOrBlank() && streams.size < 2) {
            candidates += Candidate("Server1", appendQuery(masterUrl, mapOf("ub" to "1")))
            candidates += Candidate("Server2", appendQuery(masterUrl, mapOf("ab" to "1")))
        }
        candidates.forEach { candidate ->
            val resolved = resolveUrl(candidate.url, embedUrl)
            if (resolved.isBlank()) return@forEach
            val query = params.toMutableMap()
            if (fhd && query["h"].isNullOrBlank()) query["h"] = "1"
            output += StreamCenterVixStream(appendQuery(resolved, query), candidate.label)
        }
        extractDownloadUrl(html)?.let { output += StreamCenterVixStream(resolveUrl(it, embedUrl), "Download", true) }
        if (output.isEmpty() && !masterUrl.isNullOrBlank()) {
            output += StreamCenterVixStream(
                appendQuery(resolveUrl(masterUrl, embedUrl), params + if (fhd) mapOf("h" to "1") else emptyMap()),
                "Master",
            )
        }
        return output.distinctBy { it.url }
    }

    private data class Candidate(val label: String, val url: String)

    private fun extractStreams(html: String): List<Candidate> {
        val raw = Regex("window\\.streams\\s*=\\s*(\\[[\\s\\S]*?]);").find(html)?.groupValues?.getOrNull(1) ?: return emptyList()
        runCatching {
            val array = JSONArray(raw)
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = normalize(item.optString("url"))
                    if (url.isNotBlank()) add(Candidate(item.optString("name").ifBlank { "Server${index + 1}" }, url))
                }
            }
        }
        return Regex("\\{[\\s\\S]*?}").findAll(raw).mapIndexedNotNull { index, match ->
            val item = match.value
            val url = Regex("url\\s*:\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE).find(item)?.groupValues?.getOrNull(1)?.let(::normalize)
                ?: return@mapIndexedNotNull null
            val name = Regex("name\\s*:\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE).find(item)?.groupValues?.getOrNull(1).orEmpty().ifBlank { "Server${index + 1}" }
            Candidate(name, url)
        }.toList()
    }

    private fun extractParams(html: String, embedUrl: String): Map<String, String> {
        val params = linkedMapOf<String, String>()
        val block = Regex("window\\.masterPlaylist\\s*=\\s*\\{[\\s\\S]*?params\\s*:\\s*\\{([\\s\\S]*?)}[\\s\\S]*?}").find(html)?.groupValues?.getOrNull(1).orEmpty()
        Regex("(?:['\"])?(token|expires|asn)(?:['\"])?\\s*:\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE).findAll(block).forEach { params[it.groupValues[1]] = it.groupValues[2] }
        listOf("token", "expires", "asn").forEach { key ->
            if (params[key].isNullOrBlank()) Regex("(?:['\"])?$key(?:['\"])?\\s*:\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.let { params[key] = it }
            if (params[key].isNullOrBlank()) query(embedUrl)[key]?.let { params[key] = it }
        }
        return params
    }

    private fun extractMasterUrl(html: String, embedUrl: String): String? {
        Regex("window\\.masterPlaylist\\s*=\\s*\\{[\\s\\S]*?url\\s*:\\s*['\"]([^'\"]+)['\"]").find(html)?.groupValues?.getOrNull(1)?.let { return resolveUrl(it, embedUrl) }
        val id = vixIdRegex.find(embedUrl)?.groupValues?.getOrNull(1)
            ?: Regex("window\\.video\\s*=\\s*\\{[\\s\\S]*?id\\s*:\\s*['\"](\\d+)").find(html)?.groupValues?.getOrNull(1)
            ?: vixIdRegex.find(normalize(html))?.groupValues?.getOrNull(1)
        return id?.let { origin(embedUrl)?.let { host -> "$host/playlist/$it" } }
    }

    private fun extractDownloadUrl(html: String): String? {
        Regex("window\\.downloadUrl\\s*=\\s*['\"]([^'\"]+)['\"]").find(html)?.groupValues?.getOrNull(1)?.let { return normalize(it) }
        return Regex("https?://[^\\s'\"<>]+(?:mp4|m3u8)[^\\s'\"<>]*", RegexOption.IGNORE_CASE).find(html)?.value?.let(::normalize)
    }

    private fun canPlayFhd(html: String, embedUrl: String): Boolean = Regex("window\\.canPlayFHD\\s*=\\s*true").containsMatchIn(html) || query(embedUrl).let { it.containsKey("canPlayFHD") || it["h"] == "1" }
    private fun normalize(value: String): String = value.replace(Regex("\\\\u([0-9a-fA-F]{4})")) { it.groupValues[1].toInt(16).toChar().toString() }.replace("\\/", "/").replace("&amp;", "&").trim()
    private fun origin(url: String): String? = originRegex.find(normalize(url))?.groupValues?.getOrNull(1)
    private fun resolveUrl(raw: String, base: String): String { val value = normalize(raw); if (value.startsWith("http://") || value.startsWith("https://")) return value; if (value.startsWith("//")) return "https:$value"; val host = origin(base) ?: return value; return if (value.startsWith("/")) "$host$value" else "$host/$value" }
    private fun query(url: String): Map<String, String> { val raw = url.substringAfter('?', "").substringBefore('#'); if (raw.isBlank()) return emptyMap(); return raw.split('&').mapNotNull { part -> val pieces = part.split('=', limit = 2); val key = decode(pieces[0]); if (key.isBlank()) null else key to decode(pieces.getOrElse(1) { "" }) }.toMap() }
    private fun appendQuery(url: String, values: Map<String, String>): String { val normalized = normalize(url); if (normalized.isBlank()) return normalized; val hash = normalized.substringAfter('#', "").takeIf { it.isNotBlank() }?.let { "#$it" }.orEmpty(); val noHash = normalized.substringBefore('#'); val base = noHash.substringBefore('?'); val merged = query(noHash).toMutableMap(); values.forEach { (key, value) -> if (value.isNotBlank() && !merged.containsKey(key)) merged[key] = value }; val query = merged.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }; return if (query.isBlank()) "$base$hash" else "$base?$query$hash" }
    private fun decode(value: String): String = runCatching { URLDecoder.decode(value.replace('+', ' '), StandardCharsets.UTF_8.name()) }.getOrDefault(value)
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
