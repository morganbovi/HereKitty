package io.sweatshop.herekitty.mobile.domain.auth

import kotlinx.coroutines.flow.StateFlow

sealed interface AuthState {
    object Uninitialized : AuthState
    data class Authenticated(val userId: String) : AuthState
    object Unauthenticated : AuthState
}

interface AuthRepository {
    val authState: StateFlow<AuthState>
    fun signOut()
    suspend fun refreshToken()
}
