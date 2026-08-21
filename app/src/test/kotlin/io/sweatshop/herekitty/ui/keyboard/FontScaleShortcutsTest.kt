package io.sweatshop.herekitty.ui.keyboard

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FontScaleShortcutsTest {

    /** `+` needs shift on most layouts, so the key actually pressed for "zoom in" is `=`. */
    @Test
    fun `equals and plus both zoom in`() {
        assertEquals(FontScaleCommand.Increase, fontScaleCommandFor(Key.Equals, isCommandPressed = true))
        assertEquals(FontScaleCommand.Increase, fontScaleCommandFor(Key.Plus, isCommandPressed = true))
        assertEquals(FontScaleCommand.Increase, fontScaleCommandFor(Key.NumPadAdd, isCommandPressed = true))
    }

    @Test
    fun `minus zooms out`() {
        assertEquals(FontScaleCommand.Decrease, fontScaleCommandFor(Key.Minus, isCommandPressed = true))
        assertEquals(
            FontScaleCommand.Decrease,
            fontScaleCommandFor(Key.NumPadSubtract, isCommandPressed = true),
        )
    }

    @Test
    fun `zero resets`() {
        assertEquals(FontScaleCommand.Reset, fontScaleCommandFor(Key.Zero, isCommandPressed = true))
        assertEquals(FontScaleCommand.Reset, fontScaleCommandFor(Key.NumPad0, isCommandPressed = true))
    }

    /** Otherwise typing a hyphen into a filter would resize the log. */
    @Test
    fun `the keys do nothing without the modifier`() {
        assertNull(fontScaleCommandFor(Key.Minus, isCommandPressed = false))
        assertNull(fontScaleCommandFor(Key.Equals, isCommandPressed = false))
        assertNull(fontScaleCommandFor(Key.Zero, isCommandPressed = false))
    }

    @Test
    fun `other combinations are left alone`() {
        assertNull(fontScaleCommandFor(Key.A, isCommandPressed = true))
        assertNull(fontScaleCommandFor(Key.One, isCommandPressed = true))
        assertNull(fontScaleCommandFor(Key.Backspace, isCommandPressed = true))
    }
}
