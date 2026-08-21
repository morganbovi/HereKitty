package io.sweatshop.herekitty.ui.notification

enum class NotificationSeverity { Info, Error }

/**
 * One balloon in the corner.
 *
 * Carries an id because several can be up at once and each dismisses on its own — a plain message
 * string could not tell two identical exports apart.
 */
data class AppNotification(
    val id: Long,
    val message: String,
    val severity: NotificationSeverity,
) {
    /**
     * Errors wait to be read. Losing "could not export this session" to a timer is worse than a
     * balloon that outstays its welcome, and the one thing you might want to copy out of it.
     */
    val dismissesOnItsOwn: Boolean get() = severity != NotificationSeverity.Error
}

/** Newest last, and never more than a handful, so a burst cannot bury the window it sits over. */
fun List<AppNotification>.plusCapped(notification: AppNotification): List<AppNotification> =
    (this + notification).takeLast(MAX_VISIBLE)

private const val MAX_VISIBLE = 4
