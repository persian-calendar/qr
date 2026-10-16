# Qr
[![](https://jitpack.io/v/persian-calendar/qr.svg)](https://jitpack.io/#persian-calendar/qr)

Encoder only headless qr code generator for Kotlin/Java/JVM.

It's a port of https://github.com/Rich-Harris/headless-qr which is adapted from
https://github.com/kazuhikoarase/qrcode-generator itself under the following license (MIT),

```
   QR Code Generator for JavaScript

   Copyright (c) 2009 Kazuhiko Arase

   URL: http://www.d-project.com/

   Licensed under the MIT license:
    http://www.opensource.org/licenses/mit-license.php

   The word 'QR Code' is registered trademark of
   DENSO WAVE INCORPORATED
    http://www.denso-wave.com/qrcode/faqpatent-e.html
```

It's actually not ported directly from the JavaScript version but from the Dart version
https://github.com/ebraminio/headless_qr as the intermediary step.

Most of it's source history exists
[here](https://github.com/persian-calendar/persian-calendar/blob/af539d1/PersianCalendar/src/main/kotlin/com/byagowi/persiancalendar/ui/converter/Qr.kt)

# Usage

Add this in your root build.gradle at the end of repositories section:
```kotlin
allprojects {
    repositories {
        ...
        maven("https://jitpack.io")
    }
}
```

Now actually add the dependency:
```kotlin
dependencies {
    implementation("com.github.persian-calendar:qr:LATEST_GIT_HASH")
}
```

For other build tools support have a look at [this](https://jitpack.io/#persian-calendar/qr).

# Tests

`jvmTest` decodes what this library encodes with
[ZXing](https://github.com/zxing/zxing), and the text has to come back exactly as it went
in. ZXing shares no code with this library, and reading the message back exercises the whole
of the standard at once: the mode and the character count, the padding, the Reed-Solomon
blocks and their error correction, the mask, the format and the version information, and the
placement of all of it. A few of the tests go through a drawn image as well, so that a
scanner's view of the code - the finder, timing and alignment patterns - is covered too.

There is also a fuzzer, which throws random payloads at the encoder and decodes every one of
them back. It is seeded, so a failure can be replayed, and both the seed and the number of
cases can be moved from the command line:

```sh
./gradlew jvmTest
./gradlew jvmTest -Dqr.fuzz.seed=12345 -Dqr.fuzz.cases=5000
```

CI runs it twice over: once inside `jvmTest` with the default 400 cases, and again in a job
of its own with 5000 cases and a seed taken from the run number, so every run explores
payloads no earlier run did. The test prints the seed it used, and the workflow can be
started by hand with `fuzz-seed` set to that number to replay the same cases.

# Web demo

There is a small demo of this library in the [web](web) module, which encodes to JavaScript
and to WebAssembly and draws the code in the browser.
