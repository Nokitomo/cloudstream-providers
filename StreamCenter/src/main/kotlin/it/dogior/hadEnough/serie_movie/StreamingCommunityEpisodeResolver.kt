package it.dogior.hadEnough.serie_movie

import org.json.JSONArray

internal object StreamingCommunityEpisodeResolver {
    fun resolve(name: String?, number: Int, translations: JSONArray?): String {
        for (index in 0 until (translations?.length() ?: 0)) {
            val item = translations?.optJSONObject(index) ?: continue
            if (item.optString("key") == "name" && item.optString("locale") == "it") {
                item.optString("value").trim().takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return name?.trim()?.takeIf(String::isNotBlank) ?: "Episode $number"
    }
}
