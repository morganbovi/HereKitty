package io.sweatshop.herekitty.domain.base

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("HereKitty")

object Log {
    fun d(message: () -> Any?) = logger.debug { message() }

    fun i(message: () -> Any?) = logger.info { message() }

    fun w(throwable: Throwable? = null, message: () -> Any?) = logger.warn(throwable) { message() }

    fun e(throwable: Throwable? = null, message: () -> Any?) = logger.error(throwable) { message() }
}
