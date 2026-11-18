# Folio feature implementation status

## Implemented local features

| Feature | macOS | Android |
|---|---:|---:|
| Create/edit/autosave, search, folders, tags | Yes | Yes |
| Pin, archive, trash, restore, duplicate | Yes | Yes |
| Starter templates and Markdown shortcuts | Yes | Yes |
| Share one note as Markdown | Yes | Yes |
| Export full-library JSON backup | Yes | Yes |
| Restore/merge full-library backup | Yes | Yes |
| Shared versioned backup schema with millisecond timestamps | Yes | Yes |

## Not implemented / not production-ready

- Full rendered rich-text blocks (tables, code highlighting, math, callouts)
- Image/file attachments, scanning and OCR (audio transcription intentionally excluded)
- PDF import, annotation and export
- Handwriting and drawing canvas
- Client UI for version history and conflict-aware merge
- Client-side account sign-in, automatic cross-device sync worker and offline outbox
- Client-side end-to-end encryption, secure key storage and recovery
- Safe Folder with biometric unlock and password fallback
- Widgets, share extensions, quick capture and menu-bar capture
- Accessibility/localization audit and automated UI/integration tests

## Backup and security notes

Backups use a shared versioned JSON envelope (`schemaVersion: 1`) with normalized field names and Unix-millisecond timestamps. Restore merges by stable note ID and prefers the newest `updatedAtMs` record. The macOS importer also accepts its older JSON-array backup format. Android imports the shared envelope.

Backups and local note stores are plaintext. Store backups privately. Do not store passwords, financial secrets or private documents in this prototype. Safe Folder and cloud sync are not implemented. Markdown shortcuts insert syntax into plain text; they are not a full rendered editor.


## Cloud sync infrastructure added

The Supabase migration `20261118000100_folio_encrypted_sync.sql` now defines per-user encrypted note envelopes, append-only revision history, attachment metadata, a private encrypted-attachment bucket, and row-level security policies. See [SYNC_SETUP.md](SYNC_SETUP.md).

**Important:** The migration is backend groundwork only. Neither native client currently performs Supabase authentication, client-side encryption, background sync, or conflict resolution. Those features must not be marked complete until the clients are implemented and tested. Do not upload private notes to this schema using plaintext payloads.
