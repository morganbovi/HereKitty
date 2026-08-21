package io.sweatshop.herekitty.domain.features.logs.repository

import io.sweatshop.herekitty.domain.features.logs.model.LogLine

interface LogSnapshot {
    val size: Int

    operator fun get(index: Int): LogLine

    /** How many identical lines this row stands for. Always 1 unless duplicates are being collapsed. */
    fun repeatCountAt(index: Int): Int

    companion object {
        val Empty = object : LogSnapshot {
            override val size = 0

            override fun get(index: Int): LogLine = throw IndexOutOfBoundsException("Empty snapshot")

            override fun repeatCountAt(index: Int): Int = 1
        }
    }
}
