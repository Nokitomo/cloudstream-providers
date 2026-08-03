package it.dogior.hadEnough

internal object StreamingCommunityImageResolver {
    fun resolve(value: String?, cdnUrl: String): String? {
        val image = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return if (image.startsWith("http://") || image.startsWith("https://")) image
        else "$cdnUrl/images/${image.trimStart('/')}"
    }
}
