package io.sweatshop.herekitty.features.session.dialogs

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplyViewConfirmationTest {

    private fun pane(id: Long, tag: String) =
        PaneConfig(PaneId(id), filter = LogFilter(tags = setOf(tag)))

    private fun view(name: String, vararg tags: String) =
        ViewConfig.of(*tags.mapIndexed { index, tag -> pane(index.toLong(), tag) }.toTypedArray())
            .copy(name = name)

    private val incoming = view("Radio debug", "Radio")

    @Test
    fun `replacing several unsaved panes asks first`() {
        val current = view("", "Device", "Node", "Outcome")

        assertTrue(shouldConfirmApply(current, incoming, hasUnsavedChanges = true))
    }

    /** One pane is not an arrangement, so there is nothing to warn about losing. */
    @Test
    fun `a single pane is replaced without asking`() {
        val current = view("", "Device")

        assertFalse(shouldConfirmApply(current, incoming, hasUnsavedChanges = true))
    }

    /** A saved setup is one click away again, so replacing it loses nothing. */
    @Test
    fun `an already saved setup is replaced without asking`() {
        val current = view("Debug", "Device", "Node", "Outcome")

        assertFalse(shouldConfirmApply(current, incoming, hasUnsavedChanges = false))
    }

    @Test
    fun `reapplying the setup already on screen does not ask`() {
        val current = view("Whatever", "Device", "Node")
        val same = view("Debug", "Device", "Node")

        assertFalse(shouldConfirmApply(current, same, hasUnsavedChanges = true))
    }

    @Test
    fun `a changed named setup still asks`() {
        val current = view("Debug", "Device", "Node", "Outcome")

        assertTrue(shouldConfirmApply(current, incoming, hasUnsavedChanges = true))
    }
}
