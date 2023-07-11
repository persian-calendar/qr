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
