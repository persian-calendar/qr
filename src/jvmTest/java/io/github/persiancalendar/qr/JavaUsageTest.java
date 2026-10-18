package io.github.persiancalendar.qr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What this library looks like from Java. The Kotlin tests check the encoder; this one is
 * here to be read as an example, and its assertions are the things a Java caller leans on.
 *
 * <p>{@code Qr} is a Kotlin value class, which on the JVM has no identity of its own: it is
 * the object it wraps, and every name it compiles to is mangled so that Java source cannot
 * write it. That is why the entry points are the static methods on {@code Qr} — {@code of},
 * {@code sizeOf} and {@code isDark} — and why {@code var} is the honest type for a code
 * here: what a Java variable holds is the matrix the value class erases to, not a boxed
 * {@code Qr}. Nothing else about the library changes: it is still immutable, and the quiet
 * zone is still drawn by whoever renders the code.
 */
public final class JavaUsageTest {

    /**
     * The margin ISO/IEC 18004 asks a scanner for, in modules, on every side.
     */
    private static final int QUIET_ZONE = 4;

    /**
     * Modules drawn larger than one pixel, which is what a scanner expects of an image.
     */
    private static final int SCALE = 4;

    private static final int BLACK = 0xff000000;
    private static final int WHITE = 0xffffffff;

    @Test
    public void encodesTextIntoAModuleMatrix() {
        // A version 1 code holds 14 bytes at the default level M, so this eleven byte text
        // fits in the smallest symbol there is.
        var code = new Qr("Hello, Java", null, null);

        // A version 1 code is 21 modules a side, and this size is the encoding region only.
        assertEquals(21, code.getSize());

        // Each of the three finder patterns has a dark outer ring and a light ring inside it.
        assertTrue("the corner of a finder pattern is dark", code.get(0, 0));
        assertFalse("the ring inside it is light", code.get(1, 1));
        assertTrue("the middle of it is dark again", code.get(3, 3));

        // A module outside the code reads as light rather than throwing, so a renderer can
        // ask about the quiet zone without a bounds check of its own.
        assertFalse("left of the code is light", code.get(-1, 10));
        assertFalse("right of the code is light", code.get(21, 10));
    }

    @Test
    public void pinsTheVersionAndTheErrorCorrectionLevel() {
        // Java has no default arguments, so both are spelled out; null means the default.
        var pinned = new Qr("pinned", ErrorCorrectionLevel.H, 5);
        assertEquals("a version 5 code is 5 * 4 + 17 modules a side", 37, pinned.getSize());

        var auto = new Qr("pinned", ErrorCorrectionLevel.H, null);
        assertEquals("auto picks the smallest version that fits", 21, auto.getSize());
    }

    @Test
    public void refusesTextThatDoesNotFitTheVersionItWasGiven() {
        // A version is a demand rather than a hint, so a text that overflows it throws
        // instead of producing a code that cannot be read.
        assertThrows(
            IllegalStateException.class,
            () -> new Qr("a url that does not fit in 14 bytes", ErrorCorrectionLevel.M, 1));
    }

    @Test
    public void readsBackThroughZxingAsAScannerWould() throws Exception {
        var text = "https://github.com/persian-calendar/qr";
        var code = new Qr(text, ErrorCorrectionLevel.M, null);

        // Nothing of the library is used to draw this: a caller builds the image it wants,
        // which is where the four module margin comes from, and ZXing reads it back.
        var size = code.getSize();
        var side = (size + QUIET_ZONE * 2) * SCALE;
        var pixels = new int[side * side];
        Arrays.fill(pixels, WHITE);
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                if (!code.get(x, y)) continue;
                for (var dy = 0; dy < SCALE; dy++) {
                    var row = (y + QUIET_ZONE) * SCALE + dy;
                    for (var dx = 0; dx < SCALE; dx++) {
                        pixels[row * side + (x + QUIET_ZONE) * SCALE + dx] = BLACK;
                    }
                }
            }
        }

        var source = new RGBLuminanceSource(side, side, pixels);
        var bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));

        assertEquals(text, new QRCodeReader().decode(bitmap, hints).getText());
    }
}
