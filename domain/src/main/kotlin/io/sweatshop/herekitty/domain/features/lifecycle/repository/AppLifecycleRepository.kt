package io.sweatshop.herekitty.domain.features.lifecycle.repository

/**
 * Tells a launch apart from a resume after an unexpected quit.
 *
 * [consumeLastExitWasClean] both reads and immediately resets the marker, so a crash between this
 * call and the next one is captured by construction rather than by remembering to clear it later.
 */
interface AppLifecycleRepository {
    fun consumeLastExitWasClean(): Boolean

    suspend fun markCleanExit()
}
