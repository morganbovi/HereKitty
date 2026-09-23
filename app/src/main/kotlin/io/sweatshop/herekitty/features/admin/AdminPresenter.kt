package io.sweatshop.herekitty.features.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.sweatshop.herekitty.domain.features.auth.AuthRepository
import io.sweatshop.herekitty.net.HttpFetch
import io.sweatshop.herekitty.ui.presenter.EventHandler
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

private const val ASSIGN_USER_TO_ORG_URL =
    "https://us-central1-herekitty-mobile.cloudfunctions.net/assignUserToOrg"

@Factory
class AdminPresenter(
    private val authRepository: AuthRepository,
) {
    @Composable
    fun present(onClose: () -> Unit): AdminUiModel {
        val scope = rememberCoroutineScope()
        var roleIsAdmin by remember { mutableStateOf(false) }
        var isSubmitting by remember { mutableStateOf(false) }
        var resultMessage by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        return AdminUiModel(
            roleIsAdmin = roleIsAdmin,
            isSubmitting = isSubmitting,
            resultMessage = resultMessage,
            error = error,
            eventHandler = EventHandler { event ->
                when (event) {
                    is AdminUiModel.Event.OnRoleIsAdminChanged -> roleIsAdmin = event.isAdmin

                    is AdminUiModel.Event.OnSubmitClicked -> scope.launch {
                        isSubmitting = true
                        error = null
                        resultMessage = null
                        try {
                            val token = authRepository.idToken()
                                ?: throw IllegalStateException("Not signed in")
                            val role = if (roleIsAdmin) "admin" else "member"
                            val body = """{"data":{"email":"${event.email}","role":"$role"}}"""
                            HttpFetch.postJson(
                                ASSIGN_USER_TO_ORG_URL,
                                body,
                                headers = mapOf("Authorization" to "Bearer $token"),
                            )
                            resultMessage = "Added ${event.email} as $role"
                        } catch (e: Exception) {
                            error = e.message ?: "Failed to add user"
                        } finally {
                            isSubmitting = false
                        }
                    }

                    AdminUiModel.Event.OnResultDismissed -> {
                        resultMessage = null
                        error = null
                    }

                    AdminUiModel.Event.OnCloseClicked -> onClose()
                }
            },
        )
    }
}
