package net.dinomite.web.unfurling

import java.net.URI

fun URI.clone(scheme: String? = this.scheme,
              userInfo: String? = this.userInfo,
              host: String? = this.host,
              port: Int? = this.port,
              path: String? = this.path,
              query: String? = this.query,
              fragment: String? = this.fragment): URI {
    return URI(scheme, userInfo, host, port ?: 80, path, query, fragment)
}
