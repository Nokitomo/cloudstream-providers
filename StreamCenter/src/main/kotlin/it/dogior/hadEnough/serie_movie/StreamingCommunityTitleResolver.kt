package it.dogior.hadEnough.serie_movie

import org.json.JSONObject

internal object StreamingCommunityTitleResolver {
    fun resolve(title: JSONObject): String {
        val translations = title.optJSONArray("translations")
        fun value(locale: String): String {
            for (index in 0 until (translations?.length() ?: 0)) {
                val item = translations?.optJSONObject(index) ?: continue
                if (item.optString("key") == "name" && item.optString("locale") == locale) return item.optString("value").trim()
            }
            return ""
        }
        val localized = value("it").ifBlank { value("en") }
        if (localized.isNotBlank()) return localized
        val slugTitle = title.optString("slug").replace(Regex("[-_]+"), " ").trim().replace(Regex("\\s+"), " ")
            .split(" ").filter(String::isNotBlank).joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        return slugTitle.ifBlank { title.optString("name") }
    }
}
