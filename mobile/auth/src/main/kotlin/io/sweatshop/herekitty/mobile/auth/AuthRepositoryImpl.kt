package io.sweatshop.herekitty.mobile.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.sweatshop.herekitty.mobile.domain.auth.AuthRepository
import io.sweatshop.herekitty.mobile.domain.auth.AuthState
import io.sweatshop.herekitty.mobile.domain.auth.AuthState.Authenticated
import io.sweatshop.herekitty.mobile.domain.auth.AuthState.Unauthenticated
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import org.koin.core.annotation.Single

@Single(binds = [AuthRepository::class])
class AuthRepositoryImpl(
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {

    override val authState = MutableStateFlow<AuthState>(AuthState.Uninitialized)

    init {
        firebaseAuth.addAuthStateListener { fa ->
            authState.value = toAuthState(fa.currentUser)
        }
    }

    override fun signOut() = firebaseAuth.signOut()

    override suspend fun refreshToken() {
        firebaseAuth.currentUser?.getIdToken(true)?.await()
            ?: error("Not authenticated")
    }

    private fun toAuthState(currentUser: FirebaseUser?): AuthState {
        val userId = currentUser?.uid
        return if (userId != null) Authenticated(userId) else Unauthenticated
    }
}
