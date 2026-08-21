package io.sweatshop.herekitty.domain.features.logs.model

enum class LogLevel(val letter: Char, val label: String) {
    VERBOSE('V', "Verbose"),
    DEBUG('D', "Debug"),
    INFO('I', "Info"),
    WARN('W', "Warn"),
    ERROR('E', "Error"),
    ASSERT('F', "Assert");

    companion object {
        fun fromLetter(letter: Char): LogLevel {
            val upper = letter.uppercaseChar()
            return entries.firstOrNull { it.letter == upper } ?: VERBOSE
        }
    }
}
