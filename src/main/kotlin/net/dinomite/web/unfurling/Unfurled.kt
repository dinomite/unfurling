package net.dinomite.web.unfurling

import java.net.URI

data class Unfurled(val url: URI,
                    val canonicalUrl: URI = url,
                    val type: Type = Type.TEXT,
                    val title: String = "",
                    val description: String = "",
                    val image: Image? = null,
                    val video: Video? = null) {
    /**
     * @return True if this unfurled has an empty title, imageUrl, and description
     */
    fun isEmpty(): Boolean {
        return title.isEmpty() && description.isEmpty() &&
                (image == null || image.isEmpty()) && (video == null || video.isEmpty())
    }
}

data class Image(val url: URI, val width: Int, val height: Int) {
    fun isEmpty(): Boolean = url.toString() == "" && width == 0 && height == 0
}

data class Video(val url: URI, val width: Int, val height: Int) {
    fun isEmpty(): Boolean = url.toString() == "" && width == 0 && height == 0
}

// TODO Text probably isn't really text, it includes things like YouTube…
enum class Type { TEXT, IMAGE, VIDEO }
