package io.sweatshop.herekitty.net

import java.util.Properties

object DesktopConfig {
    val properties: Properties by lazy {
        val props = Properties()
        DesktopConfig::class.java.getResourceAsStream("/firebase-desktop.properties")
            ?.use { props.load(it) }
        props
    }

    val relayUrl: String? by lazy {
        properties.getProperty("relayUrl")?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() }
            ?: legacyRelayUrl()
    }

    private fun legacyRelayUrl(): String? {
        val host = properties.getProperty("relayHost")?.takeIf { it.isNotBlank() } ?: return null
        val port = properties.getProperty("relayPort")?.takeIf { it.isNotBlank() } ?: "7050"
        return "ws://$host:$port"
    }
}
