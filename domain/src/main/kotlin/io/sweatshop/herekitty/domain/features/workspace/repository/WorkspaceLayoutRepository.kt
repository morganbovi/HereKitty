package io.sweatshop.herekitty.domain.features.workspace.repository

import io.sweatshop.herekitty.domain.features.workspace.model.StoredLayout

interface WorkspaceLayoutRepository {
    /** Read once at startup; a missing or unreadable file is simply an empty layout. */
    fun load(): StoredLayout

    fun save(layout: StoredLayout)
}
