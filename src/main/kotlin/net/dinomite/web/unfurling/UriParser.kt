package net.dinomite.web.unfurling

import org.slf4j.LoggerFactory
import java.net.URI

private val uriRegex: Regex = Regex("""^(([^:\/?#]+):)?(\/\/([^\/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?$""")
private val userInfoRegex: Regex = Regex("""^([^\[\]]*)@""")
private val portRegex: Regex = Regex(""":([^:@\[\]]*?)$""")

/**
 * Pares a URI with utter disregard to legality.  See Addressable::URI.parse
 */
internal fun parseUri(uri: String): URI? {
    val matchResult = uriRegex.matchEntire(uri) ?: return null

    val scheme = matchResult.groups[2]?.value
    val authority = matchResult.groups[4]?.value
    val path = matchResult.groups[5]?.value
    val query = matchResult.groups[7]?.value
    val fragment = matchResult.groups[9]?.value

    var host = ""
    var port = -1
    if (authority != null) {
        host = authority.replace(userInfoRegex, "").replace(portRegex, "")

        val portMatches = portRegex.find(authority)
        if (portMatches != null) {
            port = portMatches.groups[1]?.value?.toInt() ?: -1
        }
    }

    return URI(scheme, null, host, port, path, query, fragment)
}

internal fun parseUriSafe(uri: String): URI {
    try {
        return parseUri(uri) ?: URI("")
    } catch (e: Exception) {
        LoggerFactory.getLogger("UriParser").warn("Problem while parsing URI <$uri>: " + e.message)
        return URI("")
    }
}
