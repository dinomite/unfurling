package net.dinomite.web.unfurling

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.google.common.base.Charsets
import com.google.common.io.CharStreams
import com.google.common.io.Resources
import com.google.common.net.HttpHeaders
import org.apache.http.impl.client.HttpClients
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.util.zip.GZIPInputStream

class UnfurlingServiceTest {
    var wireMockServer: WireMockServer
    var service: UnfurlingService
    var origin: String
    var path: String

    init {
        wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMockServer.start()
        origin = "http://localhost:${wireMockServer.port()}"
        path = "/foo/bar"

        val httpClient = HttpClients.createDefault()
        service = UnfurlingService(httpClient)
    }

    @After
    fun tearDown() {
        wireMockServer.resetMappings()
    }

    @Test
    fun unfurl() {
        val requestUrl = origin + path
        wireMockServer.stubFor(get(urlEqualTo(path))
            .willReturn(aResponse()
                    .withStatus(200)
                    .withBody(gzipFixture("fixtures/medium.html.gz"))
            )
        )

        val unfurled = service.unfurl(URI(requestUrl))

        wireMockServer.verify(getRequestedFor(urlPathMatching(path)))
        assertEquals(requestUrl, unfurled.url.toString())
        assertEquals("Everything you ever wanted to know about unfurling but were afraid to ask /or/ How to make your… — Slack Platform Blog", unfurled.title)
        assertEquals("https://cdn-images-1.medium.com/max/1600/1*QOMaDLcO8rExD0ctBV3BWg.png", unfurled.image.url.toString())
        assertEquals("Let’s start with the most obvious question first. This is what an “unfurl” is:", unfurled.description)
    }

    @Test
    fun unfurl_ServerReturns500_ReturnsEmptyUnfurled() {
        val requestUrl = origin + path
        wireMockServer.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(500)
                )
        )

        val unfurled = service.unfurl(URI(requestUrl))

        wireMockServer.verify(getRequestedFor(urlPathMatching(path)))
        assertTrue(unfurled.isEmpty())
    }

    @Test
    fun unfurl_HandlesIllegalMediaType() {
        val requestUrl = origin + path
        wireMockServer.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "text/html;;charset=UTF-8")
                        .withStatus(200)
                        .withBody(gzipFixture("fixtures/medium.html.gz"))
                )
        )

        val unfurled = service.unfurl(URI(requestUrl))

        wireMockServer.verify(getRequestedFor(urlPathMatching(path)))
        assertEquals(requestUrl, unfurled.url.toString())
        assertEquals("Everything you ever wanted to know about unfurling but were afraid to ask /or/ How to make your… — Slack Platform Blog", unfurled.title)
        assertEquals("https://cdn-images-1.medium.com/max/1600/1*QOMaDLcO8rExD0ctBV3BWg.png", unfurled.image.url.toString())
        assertEquals("Let’s start with the most obvious question first. This is what an “unfurl” is:", unfurled.description)
    }

    @Test
    fun unfurl_Jpeg_ReturnsFullURIAndFilename() {
        val filename = "image.jpg"
        val fullPath = path + "/$filename"
        val requestUrl = origin + fullPath
        wireMockServer.stubFor(get(urlEqualTo(fullPath))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "image/jpg")
                        .withStatus(200)
                        .withBodyFile(filename)
                )
        )

        val unfurled = service.unfurl(URI(requestUrl))

        wireMockServer.verify(getRequestedFor(urlEqualTo(fullPath)))
        assertEquals(requestUrl, unfurled.url.toString())
        assertEquals(filename, unfurled.title)
        assertEquals(requestUrl, unfurled.image.url.toString())
        assertEquals(filename, unfurled.description)
        assertEquals(1, unfurled.image.width)
        assertEquals(1, unfurled.image.height)
    }

    @Test
    fun unfurl_AllMissing_ReturnsEmptyUnfurled() {
        val requestUrl = origin + path
        wireMockServer.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("<html><head></head></html>")
                )
        )

        val unfurled = service.unfurl(URI(requestUrl))

        wireMockServer.verify(getRequestedFor(urlPathMatching(path)))
        assertTrue(unfurled.isEmpty())
    }

    @Test
    fun unfurl_EmptyBody_ReturnsEmptyUnfurled() {
        val requestUrl = origin + path
        wireMockServer.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")
                )
        )

        val unfurled = service.unfurl(URI(requestUrl))

        wireMockServer.verify(getRequestedFor(urlPathMatching(path)))
        assertTrue(unfurled.isEmpty())
    }

    @Test
    fun getTitle_PrefersOGTitle() {
        val title = "The most title title"
        val head = Jsoup.parse("<html><head>" +
                "<meta property=\"og:title\" content=\"$title\">" +
                "<meta name=\"title\" content=\"$title\">" +
                "</head></html>").select("head").first()
        assertEquals(title, service.getTitle(head))
    }

    @Test
    fun getTitle_UsesTitleWhenOGAbsent() {
        val title = "The most title title"
        val head = Jsoup.parse("<html><head>" +
                "<meta name=\"title\" content=\"$title\">" +
                "</head></html>").select("head").first()
        assertEquals(title, service.getTitle(head))
    }

    @Test
    fun getTitle_UsesPageTitleWhenOthersAbsent() {
        val title = "The most title title"
        val head = Jsoup.parse("<html><head>" +
                "<title>$title</title>" +
                "</head></html>").select("head").first()
        assertEquals(title, service.getTitle(head))
    }

    @Test
    fun getImage_PrefersOGImage() {
        val parent = URI("http://foo.com")
        val imageUrl = URI("http://foo.com/image.jpg")
        val width = 800
        val height = 200
        val head = Jsoup.parse("<html><head>" +
                "<meta property=\"og:image\" content=\"$imageUrl\">" +
                "<meta name=\"og:image:width\" content=\"$width\">" +
                "<meta name=\"og:image:height\" content=\"$height\">" +
                "<meta name=\"twitter:image:src\" content=\"$imageUrl\">" +
                "<meta name=\"twitter:image:width\" content=\"1$width\">" +
                "<meta name=\"twitter:image:height\" content=\"1$height\">" +
                "</head></html>").select("head").first()
        assertEquals(imageUrl, service.getImage(head, parent).url)
    }

    @Test
    fun getImage_UsesTwitterImageWhenOGAbsent() {
        val parent = URI("http://foo.com")
        val imageUrl = URI("http://foo.com/image.jpg")
        val width = 800
        val height = 200
        val head = Jsoup.parse("<html><head>" +
                "<meta name=\"twitter:image:src\" content=\"$imageUrl\">" +
                "<meta name=\"twitter:image:width\" content=\"$width\">" +
                "<meta name=\"twitter:image:height\" content=\"$height\">" +
                "</head></html>").select("head").first()

        val image = service.getImage(head, parent)
        assertEquals(imageUrl, image.url)
        assertEquals(width, image.width)
        assertEquals(height, image.height)
    }

    @Test
    fun getDescription_PrefersOGDescription() {
        val description = "The most description description"
        val head = Jsoup.parse("<html><head>" +
                "<meta property=\"og:description\" content=\"$description\">" +
                "<meta name=\"description\" content=\"$description\">" +
                "</head></html>").select("head").first()
        assertEquals(description, service.getDescription(head))
    }

    @Test
    fun getDescription_UsesDescriptionWhenOGAbsent() {
        val description = "The most description description"
        val head = Jsoup.parse("<html><head>" +
                "<meta name=\"description\" content=\"$description\">" +
                "</head></html>").select("head").first()
        assertEquals(description, service.getDescription(head))
    }

    @Test
    fun fixUrl_EmptyURL_EmptyURI() {
        assertEquals(URI(""), service.fixUrl("", origin, path))
    }

    @Test
    fun fixUrl_RootRelativeURL_URIWithOrigin() {
        val image = "/foo/bar.jpg"
        assertEquals(URI(origin + image), service.fixUrl(image, origin, path))
    }

    @Test
    fun fixUrl_RelativeURL_URIWithOrigin() {
        val image = "baz.jpg"
        assertEquals(URI(origin + path + image), service.fixUrl(image, origin, path))
    }

    private fun gzipFixture(filename: String): String {
        try {
            val fileStream = GZIPInputStream(Resources.getResource(filename).openStream())
            return CharStreams.toString(InputStreamReader(fileStream, Charsets.UTF_8))
        } catch (e: IOException) {
            throw IllegalArgumentException(e)
        }
    }
}