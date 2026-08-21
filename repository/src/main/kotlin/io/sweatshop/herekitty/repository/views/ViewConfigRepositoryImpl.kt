package io.sweatshop.herekitty.repository.views

import io.sweatshop.herekitty.domain.base.AppScope
import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.domain.features.views.repository.ViewConfigRepository
import io.sweatshop.herekitty.repository.logs.SessionBundleFiles
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single(binds = [ViewConfigRepository::class])
class ViewConfigRepositoryImpl(private val appScope: AppScope) : ViewConfigRepository {

    private val directory: Path = Path.of(System.getProperty("user.home"), ".herekitty", "views")
    private val mutableSavedViews = MutableStateFlow(loadAll())
    override val savedViews = mutableSavedViews.asStateFlow()

    override fun save(view: ViewConfig) {
        if (view.name.isBlank()) return
        mutableSavedViews.value = (mutableSavedViews.value.filterNot { it.name == view.name } + view)
            .sortedBy { it.name.lowercase() }

        appScope.launch(Dispatchers.IO) {
            runCatching {
                Files.createDirectories(directory)
                Files.writeString(fileFor(view.name), ViewConfigCodec.encode(view))
            }.onFailure { Log.w(it) { "Could not save the view \"${view.name}\"" } }
        }
    }

    override fun delete(name: String) {
        mutableSavedViews.value = mutableSavedViews.value.filterNot { it.name == name }
        appScope.launch(Dispatchers.IO) {
            runCatching { Files.deleteIfExists(fileFor(name)) }
                .onFailure { Log.w(it) { "Could not delete the view \"$name\"" } }
        }
    }

    override suspend fun exportTo(view: ViewConfig, path: Path): Result<Path> = withContext(Dispatchers.IO) {
        runCatching {
            path.parent?.let { Files.createDirectories(it) }
            Files.writeString(path, ViewConfigCodec.encode(view))
            path
        }
    }

    override suspend fun importFrom(path: Path): Result<ViewConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val text = if (SessionBundleFiles.looksLikeBundle(path)) {
                SessionBundleFiles.readViewJson(path)
            } else {
                Files.readString(path)
            }
            ViewConfigCodec.decode(text, fallbackName = path.nameWithoutHereKittySuffix())
        }
    }

    private fun loadAll(): List<ViewConfig> = runCatching {
        if (!Files.isDirectory(directory)) return emptyList()
        Files.list(directory).use { entries ->
            entries.filter { it.fileName.toString().endsWith(".${ViewConfig.FILE_EXTENSION}") }
                .toList()
                .mapNotNull { file ->
                    runCatching {
                        ViewConfigCodec.decode(
                            text = Files.readString(file),
                            fallbackName = file.nameWithoutHereKittySuffix(),
                        )
                    }.onFailure { Log.w(it) { "Skipping unreadable view $file" } }.getOrNull()
                }
                .sortedBy { it.name.lowercase() }
        }
    }.getOrElse {
        Log.w(it) { "Could not list saved views in $directory" }
        emptyList()
    }

    private fun fileFor(name: String): Path = directory.resolve("${name.toFileSlug()}.${ViewConfig.FILE_EXTENSION}")

    private fun String.toFileSlug(): String =
        lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-').ifEmpty { "view" }

    private fun Path.nameWithoutHereKittySuffix(): String =
        fileName.toString()
            .removeSuffix(".${ViewConfig.FILE_EXTENSION}")
            .removeSuffix(".${SessionBundleFiles.EXTENSION}")
}
