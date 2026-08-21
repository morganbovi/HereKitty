package io.sweatshop.herekitty.repository.workspace

import io.sweatshop.herekitty.domain.base.AppScope
import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.workspace.model.StoredLayout
import io.sweatshop.herekitty.domain.features.workspace.model.StoredNode
import io.sweatshop.herekitty.domain.features.workspace.model.StoredTab
import io.sweatshop.herekitty.domain.features.workspace.repository.WorkspaceLayoutRepository
import io.sweatshop.herekitty.repository.views.ViewConfigCodec
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single(binds = [WorkspaceLayoutRepository::class])
class WorkspaceLayoutRepositoryImpl(private val appScope: AppScope) : WorkspaceLayoutRepository {

    private val file: Path = Path.of(System.getProperty("user.home"), ".herekitty", "layout.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    override fun load(): StoredLayout = runCatching {
        if (!Files.exists(file)) return StoredLayout.Empty
        val stored = json.decodeFromString<Document>(Files.readString(file))
        if (stored.herekitty != FORMAT_VERSION) return StoredLayout.Empty
        StoredLayout(stored.tabs.map { StoredTab(it.root.toDomain()) })
    }.getOrElse {
        Log.w(it) { "Could not read the saved layout from $file; starting fresh" }
        StoredLayout.Empty
    }

    override fun save(layout: StoredLayout) {
        val document = Document(tabs = layout.tabs.map { StoredTabJson(it.root.toJson()) })
        appScope.launch(Dispatchers.IO) {
            runCatching {
                Files.createDirectories(file.parent)
                Files.writeString(file, json.encodeToString(document))
            }.onFailure { Log.w(it) { "Could not save the layout to $file" } }
        }
    }

    @Serializable
    private data class Document(
        val herekitty: Int = FORMAT_VERSION,
        val tabs: List<StoredTabJson> = emptyList(),
    )

    @Serializable
    private data class StoredTabJson(val root: NodeJson)

    @Serializable
    private sealed interface NodeJson {
        @Serializable
        @SerialName("slot")
        data class Slot(
            /** The slot's view, as the same JSON a `.hkview` file holds. */
            val view: String = "",
            val deviceSerial: String? = null,
        ) : NodeJson

        @Serializable
        @SerialName("split")
        data class Split(val orientation: String, val children: List<NodeJson>) : NodeJson
    }

    private fun StoredNode.toJson(): NodeJson = when (this) {
        is StoredNode.Slot -> NodeJson.Slot(
            view = ViewConfigCodec.encode(view),
            deviceSerial = deviceSerial,
        )

        is StoredNode.Split -> NodeJson.Split(
            orientation = orientation.name,
            children = children.map { it.toJson() },
        )
    }

    private fun NodeJson.toDomain(): StoredNode = when (this) {
        is NodeJson.Slot -> StoredNode.Slot(
            view = runCatching { ViewConfigCodec.decode(view, fallbackName = "") }
                .getOrDefault(ViewConfig.Default),
            deviceSerial = deviceSerial,
        )

        is NodeJson.Split -> StoredNode.Split(
            orientation = runCatching { LayoutOrientation.valueOf(orientation) }
                .getOrDefault(LayoutOrientation.Horizontal),
            children = children.map { it.toDomain() },
        )
    }

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
