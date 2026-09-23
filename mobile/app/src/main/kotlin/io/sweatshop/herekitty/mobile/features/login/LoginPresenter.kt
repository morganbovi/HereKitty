package io.sweatshop.herekitty.mobile.features.login

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.compose.runtime.collectAsState
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import io.sweatshop.herekitty.mobile.R
import io.sweatshop.herekitty.mobile.domain.auth.AuthRepository
import io.sweatshop.herekitty.mobile.domain.auth.AuthState
import io.sweatshop.herekitty.mobile.features.login.LoginUiModel.Event.OnErrorDismissed
import io.sweatshop.herekitty.mobile.features.login.LoginUiModel.Event.OnSignInClicked
import io.sweatshop.herekitty.mobile.features.login.LoginUiModel.Event.OnSignOutClicked
import io.sweatshop.herekitty.mobile.ui.presenter.EventHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.compose.koinInject
import org.koin.core.annotation.Factory

@Factory
class LoginPresenter {

    @Composable
    fun present(): LoginUiModel {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val authRepository = koinInject<AuthRepository>()
        val authState by authRepository.authState.collectAsState()

        var isLoading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        return LoginUiModel(
            isLoading = isLoading,
            error = error,
            signedInUserId = (authState as? AuthState.Authenticated)?.userId,
            eventHandler = EventHandler { event ->
                when (event) {
                    OnSignInClicked -> scope.launch(Dispatchers.Main) {
                        isLoading = true
                        error = null
                        try {
                            signInWithGoogle(activity = context as Activity)
                        } catch (e: Exception) {
                            error = when (e) {
                                is GoogleIdTokenParsingException -> "Sign-in failed: invalid token"
                                else -> e.localizedMessage ?: "Sign-in failed"
                            }
                        } finally {
                            isLoading = false
                        }
                    }

                    OnSignOutClicked -> authRepository.signOut()
                    OnErrorDismissed -> error = null
                }
            },
        )
    }

    private suspend fun signInWithGoogle(activity: Activity) {
        val auth = FirebaseAuth.getInstance()
        val credentialManager = CredentialManager.create(activity)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(
                    activity.getString(R.string.default_web_client_id),
                ).build(),
            )
            .build()

        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Unexpected credential type")
        }

        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(
            GoogleAuthProvider.getCredential(idToken, null),
        ).await()
    }
}
