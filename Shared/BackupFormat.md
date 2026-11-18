# Folio portable backup format

Folio full-library backups use UTF-8 JSON with `schemaVersion: 1`, an `exportedAt` timestamp, and a `notes` array. Note IDs are stable UUIDs. Restore merges by note ID and prefers the newest update timestamp when both copies exist. This is a recovery policy, not a collaborative sync conflict resolver.

The clients currently serialize note records using platform-native JSON date conventions. A future shared codec migration must normalize date encoding and field names before byte-for-byte cross-platform interchange is claimed.

**Security:** backup files are plaintext. Encrypt backups before placing them in an untrusted location. Local note storage is also currently plaintext.
