package net.dinomite.web.unfurling

import java.net.URI

data class Unfurled(val url: URI,
                    val canonicalUrl: URI = url,
                    val type: Type = Type.TEXT,
                    val title: String = "",
                    val description: String = "",
                    val image: Image? = null) {
    /**
     * @return True if this unfurled has an empty title, imageUrl, and description
     */
    fun isEmpty(): Boolean {
        return title.isEmpty() && description.isEmpty() && (image == null || image.isEmpty())
    }
}

data class Image(val url: URI, val width: Int, val height: Int) {
    fun isEmpty(): Boolean = url.toString() == "" && width == 0 && height == 0
}

enum class Type { TEXT, IMAGE }
