package io.sweatshop.herekitty.repository.views

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.logs.model.LogLevel
import io.sweatshop.herekitty.domain.features.views.model.LayoutOrientation
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.PaneNode
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The on-disk form of a view. Extracted from the repository because a view is also written inside a
 * session bundle, and both places must agree byte for byte.
 *
 * Node ids are not stored: they are runtime identity, minted fresh on load, which is what stops a
 * reloaded view from looking modified.
 */
internal object ViewConfigCodec {

    const val FORMAT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun encode(view: ViewConfig): String = json.encodeToString(view.toStored())

    fun decode(text: String, fallbackName: String): ViewConfig {
        val stored = json.decodeFromString<StoredView>(text)
        require(stored.herekitty == FORMAT_VERSION) {
            "This view was written by a different HereKitty format (version ${stored.herekitty})"
        }

        var nextId = 0L
        val root = stored.root?.toPaneNode { PaneId(nextId++) }
            // A view written before panes could be nested is a single row of them.
            ?: ViewConfig.panesRow(stored.panes.map { it.toPaneConfig(PaneId(nextId++)) })

        return ViewConfig(name = stored.name.ifBlank { fallbackName }, root = root)
    }

    @Serializable
    private data class StoredView(
        val herekitty: Int = FORMAT_VERSION,
        val name: String = "",
        val root: StoredNode? = null,
        /** Superseded by [root]; still read so a view saved as a flat row keeps working. */
        val panes: List<StoredPane> = emptyList(),
    )

    @Serializable
    private sealed interface StoredNode {
        @Serializable
        @SerialName("pane")
        data class Leaf(val pane: StoredPane) : StoredNode

        @Serializable
        @SerialName("split")
        data class Split(val orientation: String, val children: List<StoredNode>) : StoredNode
    }

    @Serializable
    private data class StoredPane(
        val tags: List<String> = emptyList(),
        val excludeTags: List<String> = emptyList(),
        val query: String = "",
        val minLevel: String = LogLevel.VERBOSE.name,
        val matchCase: Boolean = false,
        val useRegex: Boolean = false,
        val followTail: Boolean = true,
        val collapseDuplicates: Boolean = false,
    )

    private fun ViewConfig.toStored() = StoredView(name = name, root = root.toStored(), panes = emptyList())

    private fun PaneNode.toStored(): StoredNode = when (this) {
        is PaneNode.Leaf -> StoredNode.Leaf(pane.toStored())
        is PaneNode.Split -> StoredNode.Split(orientation.name, children.map { it.toStored() })
    }

    private fun PaneConfig.toStored() = StoredPane(
        tags = filter.tags.toList(),
        excludeTags = filter.excludeTags.toList(),
        query = filter.query,
        minLevel = filter.minLevel.name,
        matchCase = filter.matchCase,
        useRegex = filter.useRegex,
        followTail = followTail,
        collapseDuplicates = collapseDuplicates,
    )

    private fun StoredNode.toPaneNode(nextId: () -> PaneId): PaneNode = when (this) {
        is StoredNode.Leaf -> PaneNode.Leaf(pane.toPaneConfig(nextId()))
        is StoredNode.Split -> PaneNode.Split(
            id = nextId(),
            orientation = runCatching { LayoutOrientation.valueOf(orientation) }
                .getOrDefault(LayoutOrientation.Horizontal),
            children = children.map { it.toPaneNode(nextId) },
        )
    }

    private fun StoredPane.toPaneConfig(id: PaneId) = PaneConfig(
        id = id,
        filter = LogFilter(
            tags = tags.toSet(),
            query = query,
            minLevel = runCatching { LogLevel.valueOf(minLevel) }.getOrDefault(LogLevel.VERBOSE),
            matchCase = matchCase,
            useRegex = useRegex,
            excludeTags = excludeTags.toSet(),
        ),
        followTail = followTail,
        collapseDuplicates = collapseDuplicates,
    )
}
