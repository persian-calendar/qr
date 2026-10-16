package io.github.persiancalendar.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.Decoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Decodes what this library encodes, with ZXing.
 *
 * Comparing the matrix against the library's own output would only prove that it has not
 * changed, not that it is right, so the check is a round trip through an implementation
 * that shares no code with it: ZXing decodes the code and the text has to come back exactly
 * as it went in. That covers most of the standard at once - the mode and the character
 * count, the padding, the Reed-Solomon blocks and their error correction, the mask, the
 * format and version information, and the placement of all of it - because a mistake in any
 * of them stops the decoder from reading the message back.
 *
 * ZXing is a Java library, so this lives in the jvm target. `jvmTest` already runs on CI on
 * Linux, macOS and Windows, so the coverage lands everywhere without needing a decoder for
 * each of the other targets.
 *
 * Two entry points into ZXing are used on purpose:
 *
 * - [Decoder] is given the module matrix directly, with no image between them. That is what
 *   the sweeping tests use, because it is deterministic, fast, and free of a whole class of
 *   false alarms: ZXing's *detector* is fussy about synthetic images of large, dense codes -
 *   a 2600 byte code reads back through an image at one quiet zone width and not at another
 *   - while the symbol itself is perfectly good. Worth remembering before reading a failure
 *   here as a bug in the encoder.
 * - [QRCodeReader] over a drawn image is what a scanner actually does, so some tests go
 *   through it as well, covering what the matrix level decoder cannot see: the finder,
 *   timing and alignment patterns have to be where a detector looks for them.
 */
class ZxingRoundTripTest {

    /** The text of a code, read straight from its modules. */
    private fun decodeModules(qr: Qr, what: String): String {
        assertTrue(qr.size > 0, "the encoder produced an empty code for $what")
        val matrix = BitMatrix(qr.size, qr.size)
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr[x, y]) matrix.set(x, y)
            }
        }
        // QR byte mode carries no character set of its own, so a decoder has to be told
        // which one to read the bytes with. The input is UTF-8, as the library documents.
        val hints = mapOf<DecodeHintType, Any>(DecodeHintType.CHARACTER_SET to "UTF-8")
        return try {
            Decoder().decode(matrix, hints).text
        } catch (error: Exception) {
            throw AssertionError("ZXing could not decode the modules for $what", error)
        }
    }

    /** A 4 module quiet zone, the minimum the specification asks a scanner for. */
    private val quietZone = 4

    /** Modules drawn larger than one pixel, which is what a scanner expects of an image. */
    private val scale = 4

    /** The text of a code, read out of an image of it, the way a scanner would. */
    private fun decodeImage(qr: Qr, what: String): String {
        assertTrue(qr.size > 0, "the encoder produced an empty code for $what")
        val side = (qr.size + quietZone * 2) * scale
        val pixels = IntArray(side * side) { WHITE }
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (!qr[x, y]) continue
                for (dy in 0 until scale) {
                    val row = (y + quietZone) * scale + dy
                    for (dx in 0 until scale) {
                        pixels[row * side + (x + quietZone) * scale + dx] = BLACK
                    }
                }
            }
        }
        val source = RGBLuminanceSource(side, side, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val hints = mapOf<DecodeHintType, Any>(
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )
        return try {
            QRCodeReader().decode(bitmap, hints).text
        } catch (error: Exception) {
            throw AssertionError("ZXing could not read the image for $what", error)
        }
    }

    /** The version the encoder settled on, read back from the width of the code. */
    private fun versionOf(qr: Qr): Int = (qr.size - 17) / 4

    private fun assertRoundTrip(text: String, level: ErrorCorrectionLevel? = null, version: Int? = null) {
        val what = "text of ${text.length} characters, level ${level ?: "default"}, version ${version ?: "auto"}"
        assertEquals(text, decodeModules(Qr(text, level, version), what), what)
    }

    @Test
    fun `round trips at every error correction level`() {
        val samples = listOf(
            "A",
            "Hello, QR!",
            "https://github.com/persian-calendar/qr",
            "0123456789",
            "a query string: ?a=1&b=two&c=three",
            "40 + 2",
        )
        for (level in ErrorCorrectionLevel.entries) {
            for (sample in samples) assertRoundTrip(sample, level)
        }
    }

    @Test
    fun `round trips every version from 1 to 40`() {
        // A short payload at a fixed version is padded to fill the code, so this covers the
        // padding, the character count of every size, and - through the image - the finder,
        // timing and alignment patterns of every size.
        for (version in 1..40) {
            val qr = Qr("v$version", ErrorCorrectionLevel.M, version)
            assertEquals(version * 4 + 17, qr.size, "version $version should be that wide")
            assertEquals("v$version", decodeModules(qr, "version $version"), "version $version")
            assertEquals("v$version", decodeImage(qr, "version $version as an image"), "version $version")
        }
    }

    @Test
    fun `round trips payloads that grow through the versions`() {
        // With no version given the encoder picks the smallest one that fits, so a payload
        // that grows walks its choice of version up, and each length has to survive the
        // change in how the message is spread over the Reed-Solomon blocks.
        val lengths = listOf(
            1, 7, 15, 25, 40, 60, 90, 120, 170, 230, 300, 400, 550, 700, 900,
            1100, 1400, 1700, 2000, 2300, 2600, 2900,
        )
        val seenVersions = mutableSetOf<Int>()
        for (length in lengths) {
            val text = String(CharArray(length) { ('a'.code + it % 26).toChar() })
            val qr = Qr(text)
            seenVersions += versionOf(qr)
            assertEquals(text, decodeModules(qr, "a payload of $length bytes"), "length $length")
        }
        // A check on the test itself: those lengths should have covered plenty of versions,
        // otherwise the payloads are not doing what this test assumes they do.
        assertTrue(seenVersions.size >= 15, "only reached versions $seenVersions")
    }

    @Test
    fun `round trips non latin text`() {
        // The input is UTF-8 bytes and the decoder reads them back as UTF-8, so anything
        // taking more than one byte per character has to survive as well.
        listOf(
            "سلام دنیا",
            "اعتدال بهاری",
            "Grüße aus Wien",
            "Αθήνα — 東京 — Москва",
            "🌷 نوروز مبارک 🌷",
            "café, naïve, jalapeño",
        ).forEach { assertRoundTrip(it) }
    }

    @Test
    fun `round trips the largest payload each error correction level can hold`() {
        // The byte mode capacities of version 40, straight from the specification, and one
        // byte past each of them.
        val capacities = mapOf(
            ErrorCorrectionLevel.L to 2953,
            ErrorCorrectionLevel.M to 2331,
            ErrorCorrectionLevel.Q to 1663,
            ErrorCorrectionLevel.H to 1273,
        )
        for ((level, capacity) in capacities) {
            val text = String(CharArray(capacity) { 'x' })
            val qr = Qr(text, level)
            assertEquals(40, versionOf(qr), "level $level at $capacity bytes should need version 40")
            assertEquals(text, decodeModules(qr, "level $level at $capacity bytes"), "level $level")

            assertEquals(0, Qr("$text*", level).size, "one byte past level $level should not fit")
        }
    }

    @Test
    fun `falls back to a lower error correction level when the text does not fit`() {
        // The default level is M, and the documentation says it falls back to L when the
        // text does not fit. 2400 bytes is over the 2331 that the largest code holds at M
        // and under the 2953 it holds at L, so the code below can only exist because of that
        // fallback - which is what asking for M on its own shows.
        val text = "y".repeat(2400)
        assertEquals(0, Qr(text, ErrorCorrectionLevel.M).size, "level M alone cannot hold this")
        assertEquals(text, decodeModules(Qr(text), "the fallback payload"), "the fallback payload")
    }

    @Test
    fun `refuses a fixed version that cannot hold the text`() {
        // Asking for a version is a demand rather than a hint, and there is no room to
        // overflow it, so this throws instead of returning a code that cannot be read.
        val error = assertFailsWith<IllegalStateException> {
            Qr("a url that does not fit in 14 bytes", ErrorCorrectionLevel.M, 1)
        }
        assertTrue(
            error.message?.contains("overflow") == true,
            "expected an overflow, got ${error.message}",
        )
    }

    @Test
    fun `encodes the same text the same way every time`() {
        // The mask is chosen by scoring all eight of them, so this also pins down that the
        // choice follows from the data rather than from anything that varies between runs.
        val text = "https://github.com/persian-calendar/qr"
        for (level in ErrorCorrectionLevel.entries) {
            assertEquals(render(Qr(text, level)), render(Qr(text, level)), "level $level")
        }
        // A fixed version is a demand, so a short payload is used for the small ones.
        for (version in listOf(1, 10, 25, 40)) {
            assertEquals(render(Qr("qr", null, version)), render(Qr("qr", null, version)), "version $version")
        }
    }

    private fun render(qr: Qr): String = buildString {
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) append(if (qr[x, y]) '1' else '0')
            append('\n')
        }
    }

    companion object {
        const val WHITE = -0x1 // 0xFFFFFFFF, opaque white
        const val BLACK = -0x1000000 // 0xFF000000, opaque black
    }
}
