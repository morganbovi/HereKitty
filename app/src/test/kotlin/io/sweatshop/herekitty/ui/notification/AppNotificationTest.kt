package io.sweatshop.herekitty.ui.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNotificationTest {

    private fun notice(id: Long, severity: NotificationSeverity = NotificationSeverity.Info) =
        AppNotification(id, "message $id", severity)

    @Test
    fun `notices stack newest last`() {
        val stack = emptyList<AppNotification>().plusCapped(notice(1)).plusCapped(notice(2))

        assertEquals(listOf(1L, 2L), stack.map { it.id })
    }

    /** A burst of failures must not bury the window the balloons sit over. */
    @Test
    fun `the stack drops the oldest rather than growing`() {
        val stack = (1L..10L).fold(emptyList<AppNotification>()) { acc, id -> acc.plusCapped(notice(id)) }

        assertEquals(4, stack.size)
        assertEquals(listOf(7L, 8L, 9L, 10L), stack.map { it.id })
    }

    /** Losing "could not export" to a timer is worse than a balloon that overstays. */
    @Test
    fun `errors wait to be read but notices do not`() {
        assertTrue(notice(1, NotificationSeverity.Info).dismissesOnItsOwn)
        assertFalse(notice(2, NotificationSeverity.Error).dismissesOnItsOwn)
    }
}
