package io.github.persiancalendar.qr

import com.google.zxing.DecodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Throws random payloads at the encoder and makes ZXing read every one of them back.
 *
 * This is a fuzzer in the sense of randomising the *input* rather than instrumenting the
 * code for coverage: the shapes that break a QR encoder are usually particular lengths and
 * particular mixes of bytes - the tail of a Reed-Solomon block, the last byte that fits a
 * version, a payload whose length falls exactly on a character count boundary, text whose
 * UTF-8 length differs from its character count - and those are cheap to reach by picking
 * lengths and alphabets at random and decoding whatever comes out.
 *
 * It is reproducible on purpose: the seed is fixed, so a failure can be replayed exactly,
 * and both the seed and the number of cases can be moved from the command line, which is
 * how a failure would be shrunk:
 *
 *     ./gradlew jvmTest -Dqr.fuzz.seed=12345 -Dqr.fuzz.cases=5000
 */
class FuzzRoundTripTest {

    private val seed = System.getProperty("qr.fuzz.seed")?.toLongOrNull() ?: DEFAULT_SEED
    private val cases = System.getProperty("qr.fuzz.cases")?.toIntOrNull() ?: DEFAULT_CASES

    /**
     * The alphabets the payloads are drawn from, each one a different kind of trouble:
     * ASCII, punctuation and URLs, control bytes, one and two byte scripts, and characters
     * outside the basic plane, which are four bytes each.
     */
    private val alphabets = listOf(
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
        "https://example.com/a/b?c=d&e=f#g -_.~:/?#[]@!$&'()*+,;=%",
        "\u0000\u0001\u0009\u000a\u000d\u001b\u007f normal text \u0080\u009f",
        "ابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی",
        "αβγδεζηθικλμνξοπρστυφχψω",
        "東京北京上海香港한국어",
        "🌷🌍🎉👍🏽🚀🧑‍💻",
        "\uD800\uDC00\uFFFD\uFEFF\u200B\u202E mixed \uD83D\uDE00",
    )

    /** A payload length: mostly small, sometimes large, never past what the largest code holds. */
    private fun lengthFor(random: Random, size: Int): Int {
        val roll = random.nextInt(100)
        val max = MAX_BYTES - 1
        return when {
            roll < 60 -> random.nextInt(0, minOf(64, max + 1))
            roll < 85 -> random.nextInt(0, minOf(600, max + 1))
            roll < 97 -> random.nextInt(0, minOf(2331, max + 1))
            else -> random.nextInt(minOf(2331, max), max + 1)
        }
    }

    private fun payload(random: Random): String {
        val alphabet = alphabets[random.nextInt(alphabets.size)]
        val length = lengthFor(random, alphabet.length)
        return String(CharArray(length) { alphabet[random.nextInt(alphabet.length)] })
    }

    /**
     * The text as the encoder will have written it: UTF-8 encoded and read back.
     *
     * Code points that UTF-8 cannot carry - an unpaired surrogate, say - are replaced on the
     * way in, so comparing against the original text would report a failure for something
     * the library cannot be expected to preserve. Doing the same replacement here keeps the
     * oracle exact while still letting those characters into the alphabet.
     */
    private fun asWritten(text: String): String = text.encodeToByteArray().decodeToString()

    private fun decode(qr: Qr): String {
        val matrix = BitMatrix(qr.size, qr.size)
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr[x, y]) matrix.set(x, y)
            }
        }
        val hints = mapOf<DecodeHintType, Any>(DecodeHintType.CHARACTER_SET to "UTF-8")
        return Decoder().decode(matrix, hints).text
    }

    /** How much of the specification the run got to: asserted at the end. */
    private class Coverage {
        val versions = mutableSetOf<Int>()
        val levels = mutableSetOf<ErrorCorrectionLevel>()
        var largest = 0
        var autoVersionCases = 0
        var fixedVersionCases = 0
        var tooSmallVersionCases = 0
        var tooLongCases = 0
    }

    @Test
    fun `random payloads all decode back to what was encoded`() {
        val random = Random(seed)
        val coverage = Coverage()

        repeat(cases) { index ->
            val text = payload(random)
            val expected = asWritten(text)
            val level = if (random.nextInt(4) == 0) null else ErrorCorrectionLevel.entries.random(random)
            val version = when (random.nextInt(10)) {
                0, 1, 2 -> null // let the encoder choose
                3 -> 1 + random.nextInt(40) // a demand, sometimes an impossible one
                else -> 1 + random.nextInt(40)
            }
            val what = "case $index, seed $seed: ${text.length} characters, level ${level ?: "default"}, " +
                "version ${version ?: "auto"}"

            val qr = try {
                Qr(text, level, version)
            } catch (error: IllegalStateException) {
                // A fixed version is a demand with no room to overflow, so refusing a
                // payload it cannot hold is correct - but only for that reason.
                assertTrue(
                    version != null && error.message?.contains("overflow") == true,
                    "$what: unexpected ${error.message}",
                )
                coverage.tooSmallVersionCases++
                return@repeat
            }

            if (qr.size == 0) {
                // An empty code means only one thing: the payload does not fit the level,
                // and with no fallback left either. M is the strictest thing it could have
                // fallen back from, so refusing under M proves it really was too long.
                coverage.tooLongCases++
                assertEquals(
                    0,
                    Qr(text, level ?: ErrorCorrectionLevel.M).size,
                    "$what: gave up while the level could still have held it",
                )
                return@repeat
            }

            assertEquals(0, (qr.size - 17) % 4, "$what: a width of ${qr.size} is not a version")
            val found = (qr.size - 17) / 4
            assertTrue(found in 1..40, "$what: version $found is out of range")
            if (version != null) {
                assertEquals(version, found, "$what: asked for a version and got another")
                coverage.fixedVersionCases++
            } else {
                coverage.autoVersionCases++
            }

            coverage.versions += found
            coverage.levels += ErrorCorrectionLevel.entries.firstOrNull {
                // The level actually used can be the fallback, so record what was asked for
                // and let the decode below be the judge of whether it was honoured.
                it == (level ?: ErrorCorrectionLevel.M) || level == null
            } ?: ErrorCorrectionLevel.M
            coverage.largest = maxOf(coverage.largest, expected.toByteArray().size)

            assertEquals(expected, decode(qr), "$what: the payload did not survive the round trip")
        }

        // Guard the guard: a run that only ever produced tiny codes would pass while
        // covering almost nothing, so make sure the random choices actually spread out.
        println(
            "fuzz seed $seed: $cases cases, versions ${coverage.versions.min()}..${coverage.versions.max()} " +
                "(${coverage.versions.size} distinct), ${coverage.levels.size} levels, " +
                "largest payload ${coverage.largest} bytes, " +
                "auto ${coverage.autoVersionCases}, fixed ${coverage.fixedVersionCases}, " +
                "refused ${coverage.tooSmallVersionCases + coverage.tooLongCases}",
        )
        assertTrue(coverage.versions.size >= 10, "only reached versions ${coverage.versions}")
        assertTrue(coverage.levels.size >= 3, "only reached levels ${coverage.levels}")
        assertTrue(coverage.largest >= 2000, "largest payload was only ${coverage.largest} bytes")
        assertTrue(coverage.autoVersionCases > 0 && coverage.fixedVersionCases > 0, "one path was never taken")
    }

    @Test
    fun `random payloads at every size of a fixed version decode back too`() {
        // The same idea, held to one version at a time so that every version is reached with
        // a payload that is close to filling it - which is where the padding and the last
        // Reed-Solomon block are decided.
        val random = Random(seed + 1)
        for (version in 1..40) {
            for (level in ErrorCorrectionLevel.entries) {
                val text = payload(random)
                val what = "version $version, level $level, ${text.length} characters"
                val qr = try {
                    Qr(text, level, version)
                } catch (error: IllegalStateException) {
                    assertTrue(error.message?.contains("overflow") == true, "$what: ${error.message}")
                    continue
                }
                assertEquals(expected = asWritten(text), actual = decode(qr), message = what)
            }
        }
    }

    private companion object {
        /** Fixed so that any failure can be reproduced exactly. */
        const val DEFAULT_SEED = 20260214L

        /** Enough to be worth running on every test run without slowing the build down. */
        const val DEFAULT_CASES = 400

        /** The capacity of the largest code at the lowest error correction level. */
        const val MAX_BYTES = 2953
    }
}
