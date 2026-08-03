package it.dogior.hadEnough

internal object StreamingCommunityTitleResolver {
    fun resolve(name: String?, slug: String?, translations: List<TitleTranslation>): String {
        fun value(locale: String) = translations.firstOrNull { it.key == "name" && it.locale == locale }?.value?.trim().orEmpty()
        val localized = value("it").ifBlank { value("en") }
        if (localized.isNotBlank()) return localized
        val slugTitle = slug.orEmpty().replace(Regex("[-_]+"), " ").trim().replace(Regex("\\s+"), " ")
            .split(" ").filter(String::isNotBlank).joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        return slugTitle.ifBlank { name.orEmpty() }
    }
}
