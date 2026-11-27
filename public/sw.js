const CACHE = "folio-shell-v2";
const BASE = new URL(self.registration.scope).pathname;
const SHELL = [BASE, BASE + "manifest.webmanifest", BASE + "icon.svg"];
self.addEventListener("install", event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)).then(() => self.skipWaiting())));
self.addEventListener("activate", event => event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim())));
self.addEventListener("fetch", event => {
  const requestUrl = new URL(event.request.url);
  if (event.request.method === "POST" && requestUrl.pathname === BASE + "share-target/") {
    event.respondWith((async () => {
      const form = await event.request.formData();
      const file = form.get("files");
      if (file && typeof file !== "string") {
        const cache = await caches.open("folio-shared-files");
        await cache.put(new URL(BASE + "shared-file", self.location.origin).toString(), new Response(file, { headers: { "Content-Type": file.type || "application/octet-stream", "X-Filename": file.name || "shared-file" } }));
      }
      return Response.redirect(new URL(BASE + "?shared=1", self.location.origin).toString(), 303);
    })());
    return;
  }
  if (event.request.method !== "GET" || new URL(event.request.url).origin !== self.location.origin) return;
  event.respondWith(fetch(event.request).then(response => {
    if (response.ok) caches.open(CACHE).then(cache => cache.put(event.request, response.clone()));
    return response;
  }).catch(() => caches.match(event.request).then(cached => cached || (event.request.mode === "navigate" ? caches.match(BASE) : undefined))));
});