const CACHE_NAME = 'bukukira-pwa-v4';
const APP_SHELL = [
  './',
  './index.html',
  './styles.css',
  './manifest.webmanifest',
  './icons/icon-180.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './js/firebase-config.js',
  './js/accounting-catalog.js',
  './js/strings.js',
  './js/accounting-engine.js',
  './js/license-gate.js',
  './js/export-utils.js',
  './js/firebase-repo.js',
  './js/app.js'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys
          .filter((key) => key !== CACHE_NAME)
          .map((key) => caches.delete(key))
      )
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  // Let Firebase SDK CDN, Google Fonts, and Firestore API requests use browser/IndexedDB caching directly
  if (url.origin !== self.location.origin) {
    return;
  }

  // Network-first for HTML navigation and JS/CSS/manifest so config & app updates take effect immediately
  const isCodeOrDoc =
    req.mode === 'navigate' ||
    url.pathname.endsWith('.js') ||
    url.pathname.endsWith('.css') ||
    url.pathname.endsWith('.html') ||
    url.pathname.endsWith('.webmanifest');

  if (isCodeOrDoc) {
    event.respondWith(
      fetch(req, { cache: 'no-cache' })
        .then((res) => {
          if (res && res.status === 200) {
            const copy = res.clone();
            caches.open(CACHE_NAME).then((cache) =>
              cache.put(req.mode === 'navigate' ? './index.html' : req, copy)
            );
          }
          return res;
        })
        .catch(() =>
          caches.match(req.mode === 'navigate' ? './index.html' : req, {
            ignoreSearch: true
          })
        )
    );
    return;
  }

  // Cache-first for static icons & images
  event.respondWith(
    caches.match(req, { ignoreSearch: true }).then((cached) => {
      if (cached) return cached;
      return fetch(req).then((res) => {
        if (res && res.status === 200) {
          const copy = res.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(req, copy));
        }
        return res;
      });
    })
  );
});
