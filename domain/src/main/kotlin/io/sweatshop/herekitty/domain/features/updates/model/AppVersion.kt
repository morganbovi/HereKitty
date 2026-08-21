package io.sweatshop.herekitty.domain.features.updates.model

import io.sweatshop.herekitty.domain.BuildInfo

/**
 * A version, ordered numerically rather than as text.
 *
 * Field by field, because `1.10.0` is newer than `1.9.0` and a string comparison says the opposite —
 * which would offer a downgrade as an update. A release tag's leading `v` is accepted so a GitHub
 * `tag_name` can be parsed as it arrives.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** A `-rc1` or `-beta` suffix, kept for display and, per semver, ordered *before* the release. */
    val preRelease: String = "",
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val numbers = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (numbers != 0) return numbers

        // 1.0.0-rc1 precedes 1.0.0, so running a candidate must still be offered the release.
        return when {
            preRelease == other.preRelease -> 0
            preRelease.isEmpty() -> 1
            other.preRelease.isEmpty() -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (preRelease.isEmpty()) "" else "-$preRelease"

    companion object {
        val Current: AppVersion by lazy {
            parse(BuildInfo.VERSION) ?: error("The generated version ${BuildInfo.VERSION} is unparseable")
        }

        /**
         * Null for anything that is not a version, rather than a zeroed one: a release tag that does
         * not parse must not compare as older than everything and trigger a phantom update.
         */
        fun parse(raw: String): AppVersion? {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            if (trimmed.isEmpty()) return null

            val preRelease = trimmed.substringAfter('-', missingDelimiterValue = "")
            val numbers = trimmed.substringBefore('-').split('.')
            if (numbers.isEmpty() || numbers.size > 3) return null

            val parsed = numbers.map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }

            return AppVersion(
                major = parsed[0],
                minor = parsed.getOrElse(1) { 0 },
                patch = parsed.getOrElse(2) { 0 },
                preRelease = preRelease,
            )
        }
    }
}
