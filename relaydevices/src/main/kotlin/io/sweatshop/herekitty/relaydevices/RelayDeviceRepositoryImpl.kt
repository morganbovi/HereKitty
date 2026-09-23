package io.sweatshop.herekitty.relaydevices

import io.sweatshop.herekitty.domain.features.auth.AuthRepository
import io.sweatshop.herekitty.domain.features.auth.AuthState
import io.sweatshop.herekitty.domain.features.relay.RelayDevice
import io.sweatshop.herekitty.domain.features.relay.RelayDeviceRepository
import io.sweatshop.herekitty.net.DesktopConfig
import io.sweatshop.herekitty.net.HttpFetch
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Single

private const val FIRESTORE_PROJECT = "herekitty-mobile"
private const val RTDB_BASE_URL = "https://herekitty-mobile-default-rtdb.firebaseio.com"

@Single(binds = [RelayDeviceRepository::class])
class RelayDeviceRepositoryImpl(
    private val authRepository: AuthRepository,
) : RelayDeviceRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listSharedDevices(): Result<List<RelayDevice>> = runCatching {
        val authState = authRepository.authState.value as? AuthState.Authenticated
            ?: error("Not signed in")
        val orgId = authState.orgId ?: error("Not in an org")
        val token = authRepository.idToken() ?: error("Not signed in")

        val devicesResponse = HttpFetch.text(
            "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT/databases/(default)" +
                "/documents/orgs/$orgId/devices",
            headers = mapOf("Authorization" to "Bearer $token"),
        )
        val documents = json.parseToJsonElement(devicesResponse).jsonObject["documents"] as? JsonArray
            ?: return@runCatching emptyList()

        documents.mapNotNull { doc ->
            val obj = doc.jsonObject
            val id = obj["name"]?.jsonPrimitive?.content?.substringAfterLast("/") ?: return@mapNotNull null
            val fields = obj["fields"]?.jsonObject ?: return@mapNotNull null
            if (!fields.bool("isShared")) return@mapNotNull null

            RelayDevice(
                id = id,
                ownerDisplayName = fields.string("ownerDisplayName").orEmpty(),
                name = fields.string("name").orEmpty(),
                isOnline = runCatching { readPresence(id, token) }.getOrDefault(false),
            )
        }
    }

    private suspend fun readPresence(deviceId: String, token: String): Boolean =
        HttpFetch.text("$RTDB_BASE_URL/presence/$deviceId/online.json?auth=$token").trim() == "true"

    override suspend fun relayIsReachable(): Boolean {
        val relayUrl = DesktopConfig.relayUrl ?: return false
        val uri = URI(relayUrl)
        val healthScheme = if (uri.scheme == "wss") "https" else "http"
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return runCatching {
            HttpFetch.text("$healthScheme://${uri.host}$port/health").trim() == "ok"
        }.getOrDefault(false)
    }

    private fun JsonObject.string(field: String): String? =
        this[field]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content

    private fun JsonObject.bool(field: String): Boolean =
        this[field]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content.toBoolean()
}
