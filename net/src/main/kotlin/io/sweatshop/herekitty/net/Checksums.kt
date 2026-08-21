package io.sweatshop.herekitty.net

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * SHA-256 of a file, and the reading of a `sha256sum`-style manifest.
 *
 * Streamed rather than read whole: the files being checked are installers of a hundred megabytes or
 * so, and holding one in memory to hash it would be the largest allocation the app ever makes.
 */
object Checksums {

    fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { source ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Reads `<hex>  <name>` lines, the format `sha256sum` writes.
     *
     * Names are taken as written apart from the `*` that marks a binary-mode entry, and unreadable
     * lines are skipped rather than failing the whole manifest — one stray line should not make an
     * otherwise good release unverifiable.
     */
    fun parseManifest(text: String): Map<String, String> = text.lineSequence()
        .mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2 || parts[0].length != SHA256_HEX_LENGTH) return@mapNotNull null
            if (!parts[0].all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return@mapNotNull null
            parts[1].removePrefix("*") to parts[0].lowercase()
        }
        .toMap()

    private const val SHA256_HEX_LENGTH = 64
}
