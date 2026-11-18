# Folio portable backup format

Both clients export UTF-8 JSON with `schemaVersion: 1`, `exportedAtMs` (Unix milliseconds), and a `notes` array. Each note uses stable UUID text, `title`, `body`, `folder`, `tags`, `isPinned`, `isArchived`, `isDeleted`, `createdAtMs`, and `updatedAtMs`. All timestamps are Unix milliseconds, so backup files are interchangeable between macOS and Android.

Restore merges by stable note ID and prefers the newest `updatedAtMs` when both copies exist. This is a backup recovery policy, not a collaborative sync conflict resolver. The macOS importer also accepts the older macOS JSON-array format.

**Security:** backups are plaintext. Encrypt backups before placing them in an untrusted location. Local note storage is also currently plaintext.
