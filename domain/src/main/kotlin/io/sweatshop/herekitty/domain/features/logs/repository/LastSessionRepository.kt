package io.sweatshop.herekitty.domain.features.logs.repository

import io.sweatshop.herekitty.domain.features.logs.model.LastSessionInfo

/**
 * The device sessions a clean quit saved to disk, keyed by serial, so a slot that cannot reattach to
 * its device can still open what it last held instead of sitting empty.
 */
interface LastSessionRepository {
    fun find(serial: String): LastSessionInfo?

    /** Every serial with a save on disk, newest first. */
    fun findAll(): List<LastSessionInfo>

    /** Snapshots every still-live device session to disk, overwriting that serial's previous save. */
    suspend fun saveAll(sessions: List<LogSession>)
}
