// The revision the page was built from, put here when the site is deployed: the browser only
// looks for a new service worker when sw.js itself has changed, so a deploy has to change it
// to be noticed. A new worker means a new cache, and `activate` drops the one before it. An
// ordinary build leaves it at 'dev', where the name of the cache does not matter.
const BUILD = 'dev';

const CACHE = 'qr-web-' + BUILD;

const PRECACHE = [
  './',
  './index.html',
  './main.js',
  './web.js',
  './manifest.webmanifest',
  './icons/icon.svg',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(PRECACHE)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;

  // The page goes to the network first, so a new deploy is picked up as soon as the browser
  // is online, and falls back to the cache so the page still opens offline.
  if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(event.request, copy));
          return response;
        })
        .catch(() => caches.match(event.request).then((cached) => cached || caches.match('./index.html')))
    );
    return;
  }

  // Everything else is served from the cache when it is there and refreshed in the
  // background, so a redeploy takes effect on the next load. The Wasm bundle is cached here
  // on first use, because its hashed file name cannot be known when this file is written.
  event.respondWith(
    caches.match(event.request).then((cached) => {
      const fromNetwork = fetch(event.request)
        .then((response) => {
          if (response && response.status === 200) {
            const copy = response.clone();
            caches.open(CACHE).then((cache) => cache.put(event.request, copy));
          }
          return response;
        })
        .catch(() => cached);
      return cached || fromNetwork;
    })
  );
});
