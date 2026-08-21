package io.sweatshop.herekitty.domain.features.settings.model

/**
 * How much to scale the log font by, as a multiplier of the console text style.
 *
 * A fixed ladder rather than a step size, so the sizes are predictable and repeatable instead of
 * drifting to whatever repeated multiplication lands on. A value from outside the ladder — an older
 * settings file, say — steps onto the nearest rung in the direction asked for.
 */
object LogFontScale {
    const val Default: Float = 1f

    val Steps: List<Float> =
        listOf(0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

    fun increased(scale: Float): Float = Steps.firstOrNull { it > scale + TOLERANCE } ?: Steps.last()

    fun decreased(scale: Float): Float = Steps.lastOrNull { it < scale - TOLERANCE } ?: Steps.first()

    fun sanitised(scale: Float): Float =
        if (scale.isFinite()) scale.coerceIn(Steps.first(), Steps.last()) else Default

    /** Wide enough that a rung never counts as larger or smaller than itself. */
    private const val TOLERANCE = 0.001f
}
