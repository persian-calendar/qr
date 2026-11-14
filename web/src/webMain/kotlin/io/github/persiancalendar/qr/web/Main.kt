@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.persiancalendar.qr.web

import io.github.persiancalendar.qr.ErrorCorrectionLevel
import io.github.persiancalendar.qr.Qr

external fun qrReady(encode: (text: String, errorCorrectionLevel: String, version: Int) -> String)

fun main() {
    qrReady { text: String, errorCorrectionLevel: String, version: Int ->
        val level = when (errorCorrectionLevel) {
            "L" -> ErrorCorrectionLevel.L
            "M" -> ErrorCorrectionLevel.M
            "Q" -> ErrorCorrectionLevel.Q
            "H" -> ErrorCorrectionLevel.H
            else -> null
        }
        // Too much text for any version is a normal thing to type, not a crash: the page is
        // handed an empty answer and says so.
        runCatching {
            val qr = Qr(text, level, version.takeIf { it in 1..40 })
            buildString(qr.size * (qr.size + 1)) {
                repeat(qr.size) { y ->
                    repeat(qr.size) { x -> append(if (qr[x, y]) '1' else '0') }
                    append('\n')
                }
            }
        }.getOrNull().orEmpty()
    }
}
