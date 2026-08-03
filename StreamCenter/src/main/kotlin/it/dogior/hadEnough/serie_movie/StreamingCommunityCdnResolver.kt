package it.dogior.hadEnough.serie_movie

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal object StreamingCommunityCdnResolver {
    fun resolve(props: JSONObject, baseUrl: String): String {
        val explicit = listOf("cdn_url", "cdnUrl", "cdn")
            .asSequence()
            .mapNotNull { key -> props.optString(key).trim().takeIf(String::isNotBlank) }
            .firstOrNull()
        val candidate = explicit?.let { if ("://" in it) it else "https://$it" }
        candidate?.toHttpUrlOrNull()?.let { return it.newBuilder().query(null).fragment(null).build().toString().trimEnd('/') }
        val base = baseUrl.toHttpUrlOrNull() ?: return ""
        val cdnHost = if (base.host.startsWith("cdn.")) base.host else "cdn.${base.host}"
        return base.newBuilder().host(cdnHost).encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')
    }
}
