# Folio feature implementation status

This file separates features present in the prototype from planned work. A feature is not complete merely because it appears in the architecture.

## Implemented local features

| Feature | macOS | Android |
|---|---:|---:|
| Create, edit and autosave notes | Yes | Yes |
| Local persistence | JSON in Application Support | SharedPreferences JSON |
| Search titles, body and tags | Yes | Yes |
| User-created folders and tags | Yes | Yes |
| Pin / unpin | Yes | Yes |
| Archive and Recently Deleted | Yes | Yes |
| Restore deleted notes | Yes | Yes |
| Duplicate a note | Yes | Yes |
| Starter templates (class, meeting, journal, project brief) | Yes | Yes |
| Markdown authoring shortcuts | Yes | Yes |
| Share/export one note as Markdown text | Yes | Yes |

## Not implemented / not production-ready

- Full rich-text rendering and structured blocks (tables, code highlighting, math rendering, callouts)
- Image, file and audio attachments; scanning and OCR
- PDF import, annotation and export
- Handwriting and drawing canvas
- Version history and conflict-aware merge
- Cross-device account, cloud sync and attachment transfer
- End-to-end encryption, secure key storage and recovery
- Safe Folder with biometric unlock and password fallback
- Widgets, share extensions, quick capture and menu-bar capture
- Accessibility/localization audit and automated UI/integration tests

## Compatibility and security notes

Android decoding supplies defaults for newly introduced fields so the initial prototype's notes remain readable. macOS decoding supplies defaults for newer note fields. Keep a backup of local data before testing development builds.

Local JSON and SharedPreferences are not encrypted. Do not store passwords, financial secrets or private documents in this prototype. Safe Folder and cloud sync are not implemented. Markdown is stored as plain text; the shortcuts insert Markdown syntax but do not provide a full rendered rich-text editor.
