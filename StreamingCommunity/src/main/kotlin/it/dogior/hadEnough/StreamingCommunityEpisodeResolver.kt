package it.dogior.hadEnough

internal object StreamingCommunityEpisodeResolver {
    fun resolve(name: String?, number: Int, translations: List<TitleTranslation>): String {
        val translated = translations.firstOrNull { it.key == "name" && it.locale == "it" }?.value?.trim().orEmpty()
        return translated.ifBlank { name?.trim().orEmpty() }.ifBlank { "Episode $number" }
    }
}
