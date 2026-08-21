package io.sweatshop.herekitty.domain.features.views.repository

import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import java.nio.file.Path
import kotlinx.coroutines.flow.StateFlow

interface ViewConfigRepository {
    val savedViews: StateFlow<List<ViewConfig>>

    fun save(view: ViewConfig)

    fun delete(name: String)

    suspend fun exportTo(view: ViewConfig, path: Path): Result<Path>

    suspend fun importFrom(path: Path): Result<ViewConfig>
}
