# Folio feature implementation status

This file separates implemented local features from planned modules. A feature is not complete merely because it appears in the architecture.

## Implemented in the current local-first foundation

| Feature | macOS | Android |
|---|---:|---:|
| Create, edit and autosave notes | Yes | Yes |
| Local persistence | JSON in Application Support | SharedPreferences JSON |
| Search title and body | Yes | Yes |
| Search tags | Yes | Yes |
| User-created folders | Yes | Yes |
| Comma-separated tags | Yes | Yes |
| Pin / unpin | Yes | Yes |
| Archive | Yes | Yes |
| Recently Deleted / restore | Yes | Yes |

## Still planned / not production-ready

- Rich text blocks (headings, lists, checklists, tables, code, math, callouts)
- Image, file and audio attachments; scanning and OCR
- PDF import, annotation and export
- Handwriting and drawing canvas
- Version history and conflict-aware merge
- Cross-device account, cloud sync and attachment transfer
- End-to-end encryption and key recovery
- Safe Folder with biometric unlock and password fallback
- Widgets, share extensions, quick capture and menu-bar capture
- Accessibility, localization and automated UI/integration tests

## Compatibility and security notes

Android decoding supplies defaults for newly introduced fields so the first prototype's notes remain readable. The macOS Codable schema has added non-optional fields; older local JSON needs a migration before it is imported. Back up the local notes.json file before testing.

Local JSON and SharedPreferences are not encrypted. Do not store passwords, financial secrets or private documents in this prototype. Safe Folder and cloud sync are not implemented.
