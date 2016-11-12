package net.dinomite.web.unfurling

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class UnfurledTest {
    val pageUri = URI("http://foobar.com/baz?qux")

    @Test
    fun isEmptyTrueWhenEmpty() {
        val unfurled = Unfurled(pageUri)
        assertTrue(unfurled.isEmpty())
    }

    @Test
    fun isEmptyFalseWhenNotEmpty() {
        val unfurled = Unfurled(pageUri, pageUri, Type.TEXT, "The title")
        assertFalse(unfurled.isEmpty())
    }
}
