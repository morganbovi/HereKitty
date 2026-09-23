package io.sweatshop.herekitty.relay

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelaySessionTokenVerifierTest {
    private val secret = "test-secret"
    private val verifier = RelaySessionTokenVerifier(secret)

    @Test
    fun `accepts a token for its session`() {
        assertTrue(verifier.authorizes(tokenFor("session-a"), "session-a"))
    }

    @Test
    fun `rejects a token for another session`() {
        assertFalse(verifier.authorizes(tokenFor("session-a"), "session-b"))
    }

    @Test
    fun `rejects a token signed with another secret`() {
        val token = JWT.create()
            .withIssuer(RelaySessionTokenVerifier.ISSUER)
            .withAudience(RelaySessionTokenVerifier.AUDIENCE)
            .withClaim("relaySessionId", "session-a")
            .sign(Algorithm.HMAC256("another-secret"))

        assertFalse(verifier.authorizes(token, "session-a"))
    }

    private fun tokenFor(sessionId: String): String = JWT.create()
        .withIssuer(RelaySessionTokenVerifier.ISSUER)
        .withAudience(RelaySessionTokenVerifier.AUDIENCE)
        .withClaim("relaySessionId", sessionId)
        .sign(Algorithm.HMAC256(secret))
}
