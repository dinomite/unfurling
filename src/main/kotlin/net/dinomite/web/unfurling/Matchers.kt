package net.dinomite.web.unfurling

import org.jsoup.nodes.Element

class Matcher(val cssQuery: String, val accessor: (Element?) -> String?)

object Matchers {
    val image = listOf(
            Matcher("meta[property=og:image]") { e -> e?.attr("content") },
            Matcher("meta[name=twitter:image:src]") { e -> e?.attr("content") },
            Matcher("meta[name=twitter:image") { e -> e?.attr("content") },
            Matcher("link[rel=icon]") { e -> e?.attr("href") },
            Matcher("link[rel=apple-touch-icon-precomposed and size=144x144]") { e -> e?.attr("href") },
            Matcher("link[rel=apple-touch-icon-precomposed and size=114x114]") { e -> e?.attr("href") },
            Matcher("link[rel=apple-touch-icon-precomposed and size=72x72]") { e -> e?.attr("href") },
            Matcher("link[rel=apple-touch-icon-precomposed") { e -> e?.attr("href") },
            Matcher("link[rel=shortcut icon]") { e -> e?.attr("href") }
    )

    val imageWidth = listOf(
            Matcher("meta[property=og:image:width]") { e -> e?.attr("content") },
            Matcher("meta[name=twitter:image:width]") { e -> e?.attr("content") }
    )

    val imageHeight = listOf(
            Matcher("meta[property=og:image:height]") { e -> e?.attr("content") },
            Matcher("meta[name=twitter:image:height]") { e -> e?.attr("content") }
    )

    val description = listOf(
            Matcher("meta[property=og:description]", { e -> e?.attr("content") }),
            Matcher("meta[name=twitter:description]", { e -> e?.attr("content") }),
            Matcher("meta[name=description]", { e -> e?.attr("content") })
    )

    val title = listOf(
            Matcher("meta[property=og:title]", { e -> e?.attr("content") }),
            Matcher("meta[name=twitter:title]", { e -> e?.attr("content") }),
            Matcher("meta[name=title]", { e -> e?.attr("content") }),
            Matcher("title", { e -> e?.text() })
    )

    val canonicalUrl = listOf(
            Matcher("link[rel='canonical']", { e -> e?.attr("href") })
    )
}
