package net.dinomite.web.unfurling

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.URISyntaxException

class UriParserTest {
    @Test
    fun testParseUri() {
        val parsed = parseUri("https://www.youtube.com/watch?v=cTZC-j48JWg")
        assertEquals("https", parsed!!.scheme)
        assertEquals("www.youtube.com", parsed.host)
        assertEquals(-1, parsed.port)
        assertEquals("/watch", parsed.path)
        assertEquals("v=cTZC-j48JWg", parsed.query)
        assertEquals(null, parsed.fragment)
    }

    @Test
    fun testParseUri_Localhost() {
        val parsed = parseUri("http://localhost")
        assertEquals("http", parsed!!.scheme)
        assertEquals("localhost", parsed.host)
        assertEquals("", parsed.path)
        assertEquals(null, parsed.query)
    }

    @Test
    fun testParseUri_HandlesPort() {
        val parsed = parseUri("http://localhost:6300/baz")
        assertEquals("http", parsed!!.scheme)
        assertEquals("localhost", parsed.host)
        assertEquals(6300, parsed.port)
        assertEquals("/baz", parsed.path)
        assertEquals(null, parsed.query)
    }

    @Test
    fun testParseUri_Bing() {
        val parsed = parseUri("https://www.bing.com/search?q=<imgsrc={iconpath}style={imgstyle}/>")
        assertEquals("https", parsed!!.scheme)
        assertEquals("www.bing.com", parsed.host)
        assertEquals("/search", parsed.path)
        assertEquals("q=<imgsrc={iconpath}style={imgstyle}/>", parsed.query)
    }

    @Test
    fun testParseUri_Rfc2396() {
        val parsed = parseUri("http://www.ietf.org/rfc/rfc2396.txt")
        assertEquals("http", parsed!!.scheme)
        assertEquals("www.ietf.org", parsed.host)
        assertEquals("www.ietf.org", parsed.authority)
        assertEquals(-1, parsed.port)
        assertEquals("/rfc/rfc2396.txt", parsed.path)
        assertEquals(null, parsed.query)
    }

    @Test
    fun testParseUri_EqualsUnparsedString() {
        val url = "http://example.com"
        val parsed = parseUri(url)
        assertEquals(url, parsed.toString())
    }

    @Test
    fun testParseUri_IncludesLeadingSlashInPath() {
        assertEquals("/path", parseUri("http://example.com/path")!!.path)
    }

    @Test
    fun testParseUri_IncludesTrailingSlashInPath() {
        assertEquals("/path/", parseUri("http://example.com/path/")!!.path)
    }

    @Test
    fun testParseUri_ErrorForEmptyString() {
        try {
            parseUri("")
            fail("Expected exception")
        } catch (e: URISyntaxException) {
            assertEquals("Expected authority at index 2: //", e.message)
        }
    }

    @Test
    fun testParseUri_ErrorsForNoHierarchicalPart() {
        try {
            parseUri("http:")
            fail("Expected exception")
        } catch (e: URISyntaxException) {
            assertEquals("Expected authority at index 7: http://", e.message)
        }
    }

    @Test
    fun testParseUri_ErrorsForLoneQuote() {
        try {
            parseUri("local\"host")
            fail("Expected exception")
        } catch (e: URISyntaxException) {
            assertEquals("Illegal character in hostname at index 7: //local%22host", e.message)
        }
    }

    @Test
    fun testParseUriSafe_SwallowsAndLogsExceptions() {
        parseUriSafe("")
        parseUriSafe("http:")
        parseUriSafe("local\"host")
    }
}
