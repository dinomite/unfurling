package net.dinomite.web.unfurling

import com.google.common.net.MediaType
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.ConnectTimeoutException
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
import javax.imageio.ImageIO
import javax.net.ssl.SSLHandshakeException

class UnfurlingService
constructor(val httpClient: CloseableHttpClient) {
    private val logger = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Unfurl a URL.  Preference is given first to Facebook Opengraph, followed by Twitter Card,
     * and finally HTML meta tags.  See Matchers for more detail.
     *
     * @return An Unfurled with the summary information.  Any Unfurled values for which the Matchers cannot divine
     * will be empty.  If the URI cannot be contacted successfully (HTTP status other that 200 OK), the Unfurled object
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
                    return buildUnfurledFromImage(uri, response)
                } else {
                    return buildUnfurledFromHtml(uri, response)
                }
            }
        } catch (e: SSLHandshakeException) {
            logger.warn("SSL error trying to get summary: " + e.message)
        } catch (e: SocketTimeoutException) {
            logger.warn("Timeout trying to get summary: " + e.message)
        } catch (e: ConnectTimeoutException) {
            logger.warn("Timeout trying to get summary: " + e.message)
        } catch (e: HttpHostConnectException) {
            logger.warn("Unable to connect to server trying to get summary: " + e.message)
        } catch (e: Exception) {
            logger.warn("Failed to handle exception case: " + e.message)
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
    private fun buildUnfurledFromImage(uri: URI, response: HttpResponse): Unfurled {
        val image = ImageIO.read(response.entity.content)
        val filename = File(uri.path).name
        val width = image.width
        val height = image.height

        return Unfurled(uri, uri, Type.IMAGE, filename, filename, Image(uri, width, height))
    }

    /**
     * Build an Unfurled for an HTML page
     */
    private fun buildUnfurledFromHtml(uri: URI, response: HttpResponse): Unfurled {
        val entity = response.entity ?: return Unfurled(uri)
        val entityString = EntityUtils.toString(entity, Charsets.UTF_8)

        if (entityString == null || entityString.isEmpty()) {
            return Unfurled(uri)
        }

        val document = Jsoup.parse(entityString)
        val head = document.select("head").first()

        val title = getTitleFromMetadata(head)
        val description = getDescriptionFromMetadata(head)
        val image = getImageFromMetadata(head, uri)

        var canonicalUrl = getCanonicalUrlFromMetadata(head)
        if (!canonicalUrl.toASCIIString().isBlank()) {
            // Substitute original URI values for critical parts if blank
            if (canonicalUrl.scheme == null || canonicalUrl.scheme.isBlank()) {
                canonicalUrl = canonicalUrl.clone(scheme = uri.scheme)
            }

            if (canonicalUrl.host == null || canonicalUrl.host.isBlank()) {
                canonicalUrl = canonicalUrl.clone(host = uri.host)
            }
        }

        return Unfurled(uri, canonicalUrl, Type.TEXT, title, description, image)
    }

    internal fun getCanonicalUrlFromMetadata(head: Element): URI {
        return parseUriSafe(getValueFromMetadata(head, Matchers.canonicalUrl, "canonicalUrl"))
    }

    internal fun getTitleFromMetadata(head: Element): String {
        return getValueFromMetadata(head, Matchers.title, "title")
    }

    internal fun getDescriptionFromMetadata(head: Element): String {
        return getValueFromMetadata(head, Matchers.description, "description")
    }

    /**
     * Retrieve the {@link Image} from the head's metadata.
     *
     * @param   head The head element containing metadata
     * @param   url  The page's URL, used to build absolute URLs for the Image
     */
    internal fun getImageFromMetadata(head: Element, url: URI): Image {
        val rawUrl = getValueFromMetadata(head, Matchers.image, "imageUrl")
        val imageUrl = fixUrl(rawUrl, url.scheme, url.authority, url.path)

        var width = 0
        try {
            width = getValueFromMetadata(head, Matchers.imageWidth, "imageWidth").toInt()
        } catch (e: NumberFormatException) {
            logger.info("Image width isn't an integer", e.message)
        }
        var height = 0
        try {
            height = getValueFromMetadata(head, Matchers.imageHeight, "imageHeight").toInt()
        } catch (e: NumberFormatException) {
            logger.info("Image height isn't an integer", e.message)
        }

        return Image(imageUrl, width, height)
    }

    internal fun getValueFromMetadata(head: Element, matchers: List<Matcher>, thing: String): String {
        for (matcher: Matcher in matchers) {
            val element = head.select(matcher.cssQuery)?.first()

            val value: String? = matcher.accessor.invoke(element)
            if (value != null) {
                return value
            }
        }

        logger.debug("Value not found for $thing")
        return ""
    }

    /**
     * Add origin to ambiguous references if necessary
     *
     * @param   subject     The filename, path + filename, or full URL to a resource
     * @param   scheme      The scheme to add if the given subject is lacking
     * @param   authority   The authority if not specified in the subject
     * @param   path        The path to prepend if the given subject has a relative path
     */
    internal fun fixUrl(subject: String, scheme: String, authority: String, path: String): URI {
        if (subject.isEmpty()) {
            return URI("")
        }

        val origin = scheme + "://" + authority
        if (subject.startsWith("//")) {
            return URI(origin + subject.replace("//", "/"))
        }

        if (subject.startsWith("/")) {
            return URI(origin + subject)
        }

        if (URI(subject).scheme == null) {
            logger.debug("Relative path")
            return URI(origin + path + subject)
        }

        return URI(subject)
    }
}

