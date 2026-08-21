package io.sweatshop.herekitty.features.session.dialogs

import io.sweatshop.herekitty.domain.features.logs.model.LogFilter
import io.sweatshop.herekitty.domain.features.views.model.PaneConfig
import io.sweatshop.herekitty.domain.features.views.model.PaneId
import io.sweatshop.herekitty.domain.features.views.model.ViewConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloseConfirmationTest {

    private fun view(name: String) =
        ViewConfig(name, ViewConfig.panesRow(listOf(PaneConfig(PaneId(0L), LogFilter(tags = setOf("Radio"))))))

    @Test
    fun `a named view with changes is worth asking about`() {
        assertTrue(shouldConfirmClose(view("flow"), hasUnsavedChanges = true, askingEnabled = true))
    }

    /** A setup you never named is a scratch pad; asking every time is nagging. */
    @Test
    fun `an unnamed setup closes without a word`() {
        assertFalse(shouldConfirmClose(view(""), hasUnsavedChanges = true, askingEnabled = true))
        assertFalse(shouldConfirmClose(view("   "), hasUnsavedChanges = true, askingEnabled = true))
    }

    @Test
    fun `a saved view with nothing to save closes quietly`() {
        assertFalse(shouldConfirmClose(view("flow"), hasUnsavedChanges = false, askingEnabled = true))
    }

    @Test
    fun `turning the prompt off silences it entirely`() {
        assertFalse(shouldConfirmClose(view("flow"), hasUnsavedChanges = true, askingEnabled = false))
    }
}
