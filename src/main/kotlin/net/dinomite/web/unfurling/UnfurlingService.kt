package net.dinomite.web.unfurling

import com.google.common.net.MediaType
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.util.EntityUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File
import java.net.SocketTimeoutException
import java.net.URI
import javax.net.ssl.SSLHandshakeException

class UnfurlingService
constructor(val httpClient: CloseableHttpClient) {
    private val logger = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Unfurl a URL.  Preference is given first to Facebook Opengraph, followed by Twitter Card,
     * and finally HTML meta tags.  See Matchers for more detail.
     *
     * @return An Unfurled with the summary information.  Any Unfurled values for which the * Matchers cannot divine
     * will be empty.If the URI cannot be contacted successfully (HTTP status other that 200 OK), the Unfurled object
     * will have empty values.
     */
    fun unfurl(uri: URI): Unfurled {
        val get = HttpGet(uri)
        try {
            MDC.put("mdc", uri.toString())
            httpClient.execute(get).use { response ->
                if (response.statusLine.statusCode != 200) {
                    return Unfurled(uri)
                }

                val mediaType = getMediaType(response)
                if (mediaType != null && mediaType.`is`(MediaType.ANY_IMAGE_TYPE)) {
                    return buildFromImage(uri, response)
                } else {
                    return buildFromHtml(uri, response)
                }
            }
        } catch (e: SSLHandshakeException) {
            logger.warn("SSL error trying to get summary: " + e.message)
        } catch (e: SocketTimeoutException) {
            logger.warn("Timeout trying to get summary: " + e.message)
        } catch (e: HttpHostConnectException) {
            logger.warn("Unable to connect to server trying to get summary: " + e.message)
        } finally {
            MDC.remove("mdc")
        }

        return Unfurled(uri)
    }

    /**
     * Extract the Media-Type from the Content-Type header
     *
     * @returns A Media-Type or null if the header didn't exist or couldn't be parsed
     */
    private fun getMediaType(response: HttpResponse): MediaType? {
        val mediaTypeHeader = response.getHeaders(HttpHeaders.CONTENT_TYPE)
        if (mediaTypeHeader?.isNotEmpty() == true) {
            try {
                return MediaType.parse(mediaTypeHeader.first().value)
            } catch (e: IllegalArgumentException) {
                logger.info("Invalid media type header " + mediaTypeHeader.first())
            }
        }

        return null
    }

    /**
     * Build an Unfurled for a raw image. This just uses the URI for all values.
     */
    private fun buildFromImage(uri: URI, response: HttpResponse): Unfurled {
        val filename = File(uri.path).name
        // TODO Get width & height from image entity
        val width = 0
        val height = 0

        return Unfurled(uri, uri, Type.IMAGE, filename, filename, Image(uri, width, height))
    }

    /**
     * Build an Unfurled for an HTML page
     */
    private fun buildFromHtml(uri: URI, response: HttpResponse): Unfurled {
        val entity = response.entity ?: return Unfurled(uri)
        val entityString = EntityUtils.toString(entity, Charsets.UTF_8) ?: return Unfurled(uri)

        val document = Jsoup.parse(entityString)
        val head = document.select("head").first()

        val title = getTitle(head)
        val description = getDescription(head)
        val image = getImage(head, uri)
        return Unfurled(uri, getCanonicalUrl(head), Type.TEXT, title, description, image)
    }

    fun getCanonicalUrl(head: Element): URI {
        return URI(getValue(head, Matchers.canonicalUrl, "canonicalUrl"))
    }

    fun getTitle(head: Element): String {
        return getValue(head, Matchers.title, "title")
    }

    fun getDescription(head: Element): String {
        return getValue(head, Matchers.description, "description")
    }

    fun getImage(head: Element, parent: URI): Image {
        val rawUrl = getValue(head, Matchers.image, "imageUrl")
        val imageUrl = fixUrl(rawUrl, parent.scheme + "://" + parent.authority, parent.path)

        var width = 0
        try {
            width = getValue(head, Matchers.imageWidth, "imageWidth").toInt()
        } catch (e: NumberFormatException) {
            logger.info("Image width isn't an integer", e)
        }
        var height = 0
        try {
            height = getValue(head, Matchers.imageHeight, "imageHeight").toInt()
        } catch (e: NumberFormatException) {
            logger.info("Image width isn't an integer", e)
        }

        return Image(imageUrl, width, height)
    }

    fun getValue(head: Element, matchers: List<Matcher>, thing: String): String {
        for (matcher: Matcher in matchers) {
            val element = head.select(matcher.cssQuery)?.first()

            val value: String? = matcher.accessor.invoke(element)
            if (value != null) {
                return value
            }
        }

        logger.info("Value not found for $thing")
        return ""
    }

    /**
     * Add origin to image paths that are lacking
     */
    fun fixUrl(image: String, origin: String, path: String): URI {
        if (image.isEmpty()) {
            return URI("")
        }

        if (image.startsWith("/")) {
            return URI(origin + image)
        }

        if (URI(image).scheme == null) {
            logger.info("Relative path")
            return URI(origin + path + image)
        }

        return URI(image)
    }
}

