package net.dinomite.unfurling

import java.net.URI

data class Unfurled(val url: URI, val title: String = "", val image: URI = URI.create(""), val description: String = "") {
    /**
     * @return True if this unfurled has an empty title, image, and description
     */
    fun isEmpty(): Boolean {
        return title.isEmpty() && image.toString().isEmpty() && description.isEmpty()
    }
}