package io.sweatshop.herekitty.domain.features.auth

import kotlinx.coroutines.flow.StateFlow

sealed interface AuthState {
    data object Uninitialized : AuthState

    data class Authenticated(
        val uid: String,
        val displayName: String,
        val email: String,
        val orgId: String?,
        val isAdmin: Boolean,
    ) : AuthState

    data object Unauthenticated : AuthState
}

/**
 * Desktop's Google sign-in has no Credential Manager equivalent to lean on, so it isn't the same
 * mechanism as the mobile app's -- it opens the system browser to Google's consent screen and
 * catches the redirect on a local loopback port (the flow Google documents for installed apps),
 * then exchanges the resulting Google ID token for a Firebase session via the plain Identity
 * Toolkit REST API. See `:auth`'s AuthRepositoryImpl for the actual mechanics.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    /**
     * Tries to pick up wherever a previous run's [signIn] left off, from the refresh token that
     * call persisted -- called once at launch. A missing or rejected token just leaves [authState]
     * as [AuthState.Unauthenticated]; there is no separate "restore failed" signal because signing
     * in again is the only recovery either way.
     */
    suspend fun restoreSession()

    /** Opens the system browser; suspends until the user completes or abandons the flow. */
    suspend fun signIn(): Result<Unit>

    fun signOut()

    /** The current Firebase ID token, for calling Firestore/Cloud Functions REST endpoints
     *  directly. Null if signed out. Known gap: does not yet refresh an expired (>1h old) token
     *  automatically -- same follow-up as session restore on launch. */
    suspend fun idToken(): String?
}
