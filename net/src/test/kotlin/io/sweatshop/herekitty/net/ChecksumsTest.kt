package io.sweatshop.herekitty.net

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChecksumsTest {

    /** The published SHA-256 of the empty input, so this pins the algorithm and not just itself. */
    @Test
    fun `hashes a file the way sha256sum does`() {
        val empty = Files.createTempFile("checksum", ".bin")

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Checksums.sha256(empty))

        Files.writeString(empty, "abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksums.sha256(empty))

        Files.deleteIfExists(empty)
    }

    @Test
    fun `reads the manifest sha256sum writes`() {
        val manifest = """
            ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  HereKitty-1.1.0-macos-arm64.dmg
            e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  HereKitty-1.1.0-linux-x64.deb
        """.trimIndent()

        val parsed = Checksums.parseManifest(manifest)

        assertEquals(2, parsed.size)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            parsed["HereKitty-1.1.0-macos-arm64.dmg"],
        )
    }

    /** `sha256sum -b` marks binary entries with a star that is not part of the name. */
    @Test
    fun `a binary-mode marker is not part of the filename`() {
        val parsed = Checksums.parseManifest(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad *HereKitty.dmg",
        )

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", parsed["HereKitty.dmg"])
    }

    @Test
    fun `hashes are compared case-insensitively by normalising to lower case`() {
        val upper = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD  x.dmg"

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksums.parseManifest(upper)["x.dmg"])
    }

    /** One stray line should not make an otherwise good release unverifiable. */
    @Test
    fun `unreadable lines are skipped rather than failing the manifest`() {
        val manifest = """
            
            # a comment nobody asked for
            not-a-hash  x.dmg
            deadbeef  too-short.dmg
            ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  good.dmg
        """.trimIndent()

        val parsed = Checksums.parseManifest(manifest)

        assertEquals(1, parsed.size)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", parsed["good.dmg"])
        assertNull(parsed["x.dmg"])
        assertNull(parsed["too-short.dmg"])
    }

    @Test
    fun `an empty manifest yields nothing rather than throwing`() {
        assertEquals(emptyMap(), Checksums.parseManifest(""))
    }
}
