package io.sweatshop.herekitty.auth

import com.sun.net.httpserver.HttpServer
import io.sweatshop.herekitty.domain.features.auth.AuthRepository
import io.sweatshop.herekitty.domain.features.auth.AuthState
import io.sweatshop.herekitty.net.DesktopConfig
import io.sweatshop.herekitty.net.HttpFetch
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Single

@Single(binds = [AuthRepository::class])
class AuthRepositoryImpl : AuthRepository {

    override val authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

    private val json = Json { ignoreUnknownKeys = true }
    private val config: Properties get() = DesktopConfig.properties
    @Volatile private var currentIdToken: String? = null

    override suspend fun idToken(): String? = currentIdToken

    override suspend fun restoreSession() {
        withContext(Dispatchers.IO) {
            runCatching {
                val refreshToken = Files.readString(tokenFile).trim()
                require(refreshToken.isNotBlank()) { "no saved refresh token" }
                refreshSession(refreshToken)
            }
        }
    }

    private suspend fun refreshSession(refreshToken: String) {
        val apiKey = config.getProperty("apiKey")
        val response = HttpFetch.postForm(
            "https://securetoken.googleapis.com/v1/token?key=$apiKey",
            mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken),
        )
        val parsed = json.parseToJsonElement(response).jsonObject
        applySession(
            idToken = parsed.getValue("id_token").jsonPrimitive.content,
            refreshToken = parsed.getValue("refresh_token").jsonPrimitive.content,
        )
    }

    override suspend fun signIn(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val clientId = config.getProperty("oauthClientId")
            val clientSecret = config.getProperty("oauthClientSecret").orEmpty()
            require(!clientId.isNullOrBlank()) {
                "oauthClientId not configured -- see auth/src/main/resources/firebase-desktop.properties.example"
            }

            val codeDeferred = CompletableDeferred<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/callback") { exchange ->
                val params = parseQuery(exchange.requestURI.query.orEmpty())
                val html = if (params["code"] != null) {
                    codeDeferred.complete(params.getValue("code"))
                    landingPage("Signed in", "You can close this tab and return to HereKitty.")
                } else {
                    val message = params["error"] ?: "sign-in was cancelled"
                    codeDeferred.completeExceptionally(RuntimeException(message))
                    landingPage("Sign-in failed", message, isError = true)
                }
                val bytes = html.toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()

            try {
                val redirectUri = "http://127.0.0.1:${server.address.port}/callback"
                val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                    "client_id=${enc(clientId)}&redirect_uri=${enc(redirectUri)}" +
                    "&response_type=code&scope=${enc("openid email profile")}" +
                    "&access_type=offline&prompt=consent"

                Desktop.getDesktop().browse(URI(authUrl))
                val code = codeDeferred.await()

                val tokenResponse = HttpFetch.postForm(
                    "https://oauth2.googleapis.com/token",
                    mapOf(
                        "code" to code,
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "redirect_uri" to redirectUri,
                        "grant_type" to "authorization_code",
                    ),
                )
                val googleIdToken = json.parseToJsonElement(tokenResponse)
                    .jsonObject.getValue("id_token").jsonPrimitive.content

                signInToFirebase(googleIdToken)
            } finally {
                server.stop(0)
            }
        }
    }

    private suspend fun signInToFirebase(googleIdToken: String) {
        val apiKey = config.getProperty("apiKey")
        val body = """
            {"postBody":"id_token=$googleIdToken&providerId=google.com",
             "requestUri":"http://localhost","returnIdpCredential":true,"returnSecureToken":true}
        """.trimIndent().replace("\n", " ")

        val response = HttpFetch.postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$apiKey",
            body,
        )
        val parsed = json.parseToJsonElement(response).jsonObject
        applySession(
            idToken = parsed.getValue("idToken").jsonPrimitive.content,
            refreshToken = parsed.getValue("refreshToken").jsonPrimitive.content,
        )
    }

    private fun applySession(idToken: String, refreshToken: String) {
        val claims = decodeJwtPayload(idToken)
        val uid = claims.getValue("sub").jsonPrimitive.content
        val displayName = claims["name"]?.jsonPrimitive?.content.orEmpty()
        val email = claims["email"]?.jsonPrimitive?.content.orEmpty()
        val orgId = claims["orgId"]?.jsonPrimitive?.content
        val isAdmin = claims["isAdmin"]?.jsonPrimitive?.content?.toBoolean() ?: false

        saveRefreshToken(refreshToken)
        currentIdToken = idToken
        authState.value = AuthState.Authenticated(uid, displayName, email, orgId, isAdmin)
    }

    override fun signOut() {
        runCatching { Files.deleteIfExists(tokenFile) }
        currentIdToken = null
        authState.value = AuthState.Unauthenticated
    }

    private fun decodeJwtPayload(jwt: String): JsonObject {
        val payloadSegment = jwt.split(".").getOrNull(1) ?: error("Malformed JWT")
        val padded = payloadSegment + "=".repeat((4 - payloadSegment.length % 4) % 4)
        val decoded = java.util.Base64.getUrlDecoder().decode(padded)
        return json.parseToJsonElement(String(decoded)).jsonObject
    }

    private val tokenFile: Path
        get() = Path.of(System.getProperty("user.home"), ".herekitty", "auth-refresh-token")

    private fun saveRefreshToken(token: String) {
        Files.createDirectories(tokenFile.parent)
        Files.writeString(tokenFile, token)
    }

    private fun parseQuery(query: String): Map<String, String> =
        if (query.isBlank()) {
            emptyMap()
        } else {
            query.split("&").associate { pair ->
                val parts = pair.split("=", limit = 2)
                parts[0] to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            }
        }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun landingPage(title: String, message: String, isError: Boolean = false): String {
        val accent = if (isError) "#E55765" else "#3574F0"
        return """
            <!doctype html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>HereKitty</title>
                <style>
                    body {
                        margin: 0;
                        height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        background: #1e1f22;
                        color: #dfe1e5;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                    }
                    .card {
                        text-align: center;
                        padding: 40px 48px;
                        border-radius: 12px;
                        background: #2b2d30;
                        box-shadow: 0 4px 24px rgba(0, 0, 0, 0.4);
                    }
                    .logo { font-size: 40px; margin-bottom: 8px; }
                    h1 { font-size: 18px; margin: 0 0 8px; color: $accent; }
                    p { font-size: 14px; color: #9da0a8; margin: 0; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="logo">🐱</div>
                    <h1>$title</h1>
                    <p>$message</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
