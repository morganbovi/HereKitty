package io.sweatshop.herekitty.domain.features.updates.repository

import io.sweatshop.herekitty.domain.features.updates.model.ReleaseInfo
import io.sweatshop.herekitty.domain.features.updates.model.UpdateState
import kotlinx.coroutines.flow.StateFlow

/**
 * Finding, staging and applying a new version of the app.
 *
 * Failures arrive through [state] rather than as return values: a check that fails on launch should
 * leave a dismissible notice, not make every caller handle an exception it cannot do anything about.
 */
interface UpdateRepository {
    val state: StateFlow<UpdateState>

    /** Asks what the newest release is. A check already in flight is not started again. */
    suspend fun check()

    /** Downloads [release], verifies it against the release's checksums, and leaves it staged. */
    suspend fun download(release: ReleaseInfo)

    /**
     * Applies a staged update and restarts.
     *
     * The swap is handed to a helper process that outlives this one, because a running app cannot
     * replace its own bundle — so this quits rather than returning.
     */
    fun applyAndRestart()

    /** Puts the state back to idle, for a notice the user has read and closed. */
    fun dismiss()
}
