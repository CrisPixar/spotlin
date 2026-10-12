package com.spotlin.messenger.data

data class ServerConfig(
    val id: String = "sxt_sv",
    val name: String = "SXT-SV",
    val httpUrl: String = "http://f1.rustix.me:31774",
    val wsUrl: String = "ws://f1.rustix.me:31774/ws",
    val isDefault: Boolean = true
) {
    companion object {
        const val DEFAULT_HTTP_URL = "http://f1.rustix.me:31774"
        const val DEFAULT_WS_URL = "ws://f1.rustix.me:31774/ws"
        val HARDCODED_SERVER = ServerConfig(
            id = "sxt_sv",
            name = "SXT-SV (f1.rustix.me:31774)",
            httpUrl = DEFAULT_HTTP_URL,
            wsUrl = DEFAULT_WS_URL,
            isDefault = true
        )

        fun resolveMediaUrl(url: String?, baseUrl: String = DEFAULT_HTTP_URL): String {
            if (url.isNullOrBlank()) return ""
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://") || url.startsWith("file://")) {
                return url
            }
            val cleanBase = baseUrl.trimEnd('/')
            val cleanPath = if (url.startsWith("/")) url else "/$url"
            return "$cleanBase$cleanPath"
        }
    }
}
