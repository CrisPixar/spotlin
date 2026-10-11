package com.spotlin.messenger.data

data class ServerConfig(
    val id: String,
    val name: String,
    val httpUrl: String,
    val wsUrl: String,
    val isDefault: Boolean = false
) {
    companion object {
        val DEFAULT_SERVERS = listOf(
            ServerConfig(
                id = "sxt_sv",
                name = "SXT-SV (f1.rustix.me)",
                httpUrl = "http://f1.rustix.me:31774",
                wsUrl = "ws://f1.rustix.me:31774/ws",
                isDefault = true
            ),
            ServerConfig(
                id = "local_dev",
                name = "Spotlin Local Server (127.0.0.1:8080)",
                httpUrl = "http://127.0.0.1:8080",
                wsUrl = "ws://127.0.0.1:8080/ws",
                isDefault = false
            )
        )
    }
}
