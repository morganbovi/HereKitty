package io.sweatshop.herekitty.relay

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

internal class RelaySessionTokenVerifier(secret: String) {
    private val verifier = JWT.require(Algorithm.HMAC256(secret))
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    fun authorizes(token: String?, sessionId: String): Boolean =
        runCatching {
            val decoded = verifier.verify(token ?: return false)
            decoded.getClaim("relaySessionId").asString() == sessionId
        }.getOrDefault(false)

    companion object {
        const val ISSUER = "herekitty-mobile"
        const val AUDIENCE = "herekitty-relay"
    }
}
