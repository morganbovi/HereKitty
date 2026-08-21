package io.sweatshop.herekitty.repository.logs

import io.sweatshop.herekitty.domain.features.logs.model.LogLine

/**
 * Whether two consecutive rows say the same thing, and so can share one row with a repeat count.
 *
 * The timestamp is deliberately ignored — repeats are only interesting because they are identical
 * apart from when they happened. The process is not, since the same message from two processes is two
 * different events.
 */
internal fun isRepeatOf(previous: LogLine?, current: LogLine): Boolean =
    previous != null &&
        previous.pid == current.pid &&
        previous.level == current.level &&
        previous.tag == current.tag &&
        previous.message == current.message
