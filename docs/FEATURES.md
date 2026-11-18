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
- Image/file/audio attachments, scanning and OCR
- PDF import, annotation and export
- Handwriting and drawing canvas
- Version history and conflict-aware merge
- Automatic cross-device account and cloud sync
- End-to-end encryption, secure key storage and recovery
- Safe Folder with biometric unlock and password fallback
- Widgets, share extensions, quick capture and menu-bar capture
- Accessibility/localization audit and automated UI/integration tests

## Backup and security notes

Backups use a shared versioned JSON envelope (`schemaVersion: 1`) with normalized field names and Unix-millisecond timestamps. Restore merges by stable note ID and prefers the newest `updatedAtMs` record. The macOS importer also accepts its older JSON-array backup format. Android imports the shared envelope.

Backups and local note stores are plaintext. Store backups privately. Do not store passwords, financial secrets or private documents in this prototype. Safe Folder and cloud sync are not implemented. Markdown shortcuts insert syntax into plain text; they are not a full rendered editor.
