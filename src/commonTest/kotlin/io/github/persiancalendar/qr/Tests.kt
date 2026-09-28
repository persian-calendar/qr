package io.github.persiancalendar.qr

import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals

class Tests {

    @Test
    fun `basic qr test`() {
        val expected = """
█▀▀▀▀▀▀▀█▀▀██▀██▀█▀▀▀██▀▀▀▀▀▀▀█
█ █▀▀▀█ █▄▀█▄▀██▄▄▀ ▀▀█ █▀▀▀█ █
█ █   █ █▄▄▀▄▀█ ▄▀▀█ ██ █   █ █
█ ▀▀▀▀▀ █▀█ ▄ █▀▄▀█ ▄ █ ▀▀▀▀▀ █
█▀█▀▀█▀▀▀▄ ▀██▄█▄ ██▄██▀██▀█▀▀█
███ ▄ █▀█▀▀▀▀▄▀  █▀ ▄▄█ ▄▄█▀▀▄█
█  █▄▀█▀▀  █▀▀█ ▀█▄▄███▄▄█ ▀█▄█
█ ▀▄█▀ ▀▄▄▀▄█ ▀ ▀▄▄▀ ██ █▀▀▀  █
█ ▄█  ▀▀  ▄ ▀ ▄  ▀▄█  ▀█ ▀██ ██
█▀█▀▄█▀▀▀▀█ ▄  ██▄▄▄█▀█▀▀█▀ ▄██
██▀▀██▀▀▀ █▄▀█▀▄█ █▀▀▀ ▀▀▀▀ ███
█▀▀▀▀▀▀▀█ ▄ ██ █ ▄▀ ▀ █▀█  ▄ ▄█
█ █▀▀▀█ █▄▀  ▀█▀▄ ▄ █ ▀▀▀ ▀▀  █
█ █   █ █ █▀▄█ ▄ █▀▄█  █▀▄▄ █ █
█ ▀▀▀▀▀ █▀▀█ █ ▄▄▄ ▀▀ ▄█▄█ █ ██
███████████████████████████████
""".trim()

        val text = "http://www.example.com/ążśźęćńół"
        val result = Qr(text)
        assertEquals(
            expected,
            (0..<ceil(result.size / 2.0).toInt() + 1).joinToString("\n") { row ->
                "█" + (0..<result.size).joinToString("") {
                    val first = !result[row * 2 - 1, it]
                    val second = !result[row * 2, it]
                    if (first) (if (second) "█" else "▀") else (if (second) "▄" else " ")
                } + "█"
            }
        )

        // val bitMatrix = QRCodeWriter().encode(
        //     text, BarcodeFormat.QR_CODE, result.size, result.size, mapOf(
        //         EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        //         EncodeHintType.MARGIN to 0
        //     )
        // )
        // result.indices.forEach { i ->
        //     val row = mutableListOf<Boolean>()
        //     result.indices.forEach { j ->
        //         row.add(bitMatrix[i, j])
        //     }
        //     println(row.joinToString("") { if (it) "*" else "." })
        // }
    }
}
