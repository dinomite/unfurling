package net.dinomite.unfurling

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
     * Unfurl a URL
     *
     * @return An Unfurled with the summary information, or null if it couldn't be retrieved or divined
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
                    return buildFromImage(uri)
                } else {
                    return buildFromHtml(uri, response)
                }
            }
        } catch (e: SSLHandshakeException) {
            logger.warn("SSL error trying to get summary for page <$uri>: " + e.message)
        } catch (e: SocketTimeoutException) {
            logger.warn("Timeout trying to get summary for page <$uri>: " + e.message)
        } catch (e: HttpHostConnectException) {
            logger.warn("Unable to connect to server trying to get summary for page <$uri>: " + e.message)
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
    private fun buildFromImage(uri: URI): Unfurled {
        val filename = File(uri.path).name
        return Unfurled(uri, filename, uri, filename)
    }

    /**
     * Build an Unfurled for an HTML page
     */
    private fun buildFromHtml(uri: URI, response: HttpResponse): Unfurled {
        val entity = response.entity ?: return Unfurled(uri)
        val entityString = EntityUtils.toString(entity, Charsets.UTF_8) ?: return Unfurled(uri)

        val document = Jsoup.parse(entityString)
        val head = document.select("head").first()
        val image = fixUrl(getImageUrl(head), uri.scheme + "://" + uri.authority, uri.path)
        return Unfurled(uri, getTitle(head), image, getDescription(head))
    }

    fun getTitle(head: Element): String {
        return getValue(head, Matchers.title, "title")
    }

    fun getImageUrl(head: Element): String {
        return getValue(head, Matchers.image, "image")
    }

    fun getDescription(head: Element): String {
        return getValue(head, Matchers.description, "description")
    }

    fun getValue(head: Element, matchers: List<Matcher>, thing: String): String {
        for (matcher: Matcher in matchers) {
            val element = head.select(matcher.cssQuery)?.first()

            val value: String? = matcher.accessor.invoke(element)
            if (value != null) {
                return value
            }
        }

        logger.warn("Value not found for $thing")
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

