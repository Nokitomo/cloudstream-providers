package it.dogior.hadEnough

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object StreamingCommunityCdnResolver {
    fun resolve(payloadCdn: String?, baseUrl: String): String {
        val explicit = payloadCdn?.trim().orEmpty()
        if (explicit.isNotBlank()) {
            val candidate = if ("://" in explicit) explicit else "https://$explicit"
            candidate.toHttpUrlOrNull()?.let { return it.newBuilder().query(null).fragment(null).build().toString().trimEnd('/') }
        }
        val base = baseUrl.toHttpUrlOrNull() ?: return ""
        val host = base.host
        val cdnHost = if (host.startsWith("cdn.")) host else "cdn.$host"
        return base.newBuilder().host(cdnHost).encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')
    }
}
