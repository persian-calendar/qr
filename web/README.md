# Qr Web

A tiny HTML demo of this library: type some text, get a QR code for it, and download it as a
PNG or an SVG. The encoding is `Qr(text)` in the browser.

## How the page is put together

The Kotlin side is the encoder and nothing else: it calls `qrReady` with a function from text
to the modules of a code, and `main.js` does the rest.

```
web/src/webMain/
├── kotlin/…/web/Main.kt   the encoder, handed to the page
└── resources/
    ├── index.html         the markup and all of the CSS
    ├── main.js            the page: the form, the canvas, the downloads, the clipboard
    ├── sw.js              the service worker, for offline use
    └── icons/             the app icon, and the PNGs generated from `icon.svg`
```

The modules cross the boundary as text, one character per module, because that is the only
shape of result both targets can hand out: the Wasm side cannot return an object, and neither
side can return an array. The largest code is 177 by 177, so the biggest string this ever
produces is about 32 KB - cheap to build once and cheap to read.

`main.js` is loaded before the bundle, because it defines `qrReady` and the bundle calls it as
soon as it runs. That order matters: the Wasm build starts up after the page has loaded, so a
value published on `window` by the Kotlin side would arrive too late.

## Run locally

From the repository root:

```sh
# JS backend (faster to build)
./gradlew :web:jsBrowserDevelopmentRun

# Wasm backend
./gradlew :web:wasmJsBrowserDevelopmentRun
```

Then open http://localhost:8080/.

## Build a static bundle

```sh
# JS bundle  -> web/build/dist/js/productionExecutable/
./gradlew :web:jsBrowserDistribution

# Wasm bundle -> web/build/dist/wasmJs/productionExecutable/
./gradlew :web:wasmJsBrowserDistribution
```

Either bundle directory is complete and can be served as it is. When the icons are
regenerated, `rsvg-convert` (from `librsvg`) has to be installed:

```sh
./gradlew :web:generateWebIcons
```

## A single file build

`webStandalone` copies the JS distribution to `web/build/dist/js/standalone/` with `main.js`
and `web.js` written into `index.html` instead of being loaded as separate files. Publishing
that directory means publishing one document, with no separate script requests and no chance
of a cached page running against a newer script:

```sh
./gradlew :web:webStandalone
```

The task leaves the normal distribution alone, so `jsBrowserDistribution` still produces the
separate files for development. Two things are adjusted in the copy: the source map comments
go, since the maps are no longer files of their own, and `main.js` and `web.js` are dropped
from the service worker's precache list, which no longer needs them.

Only the JS target is handled. The Wasm build's glue loads a `.wasm` file of its own, and that
has to stay a separate request, while the JS bundle is self-contained.
