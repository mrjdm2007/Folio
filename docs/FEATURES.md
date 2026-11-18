# Folio feature implementation status

## Implemented local features

| Feature | macOS | Android |
|---|---:|---:|
| Create/edit/autosave, search, folders, tags | Yes | Yes |
| Folder quick-filter chips | Sidebar navigation | Dynamic horizontal chips |
| Dedicated pinned-notes view | Yes | Yes |
| Responsive phone editor with return-to-list navigation | N/A | Yes |
| Live word/character count | Yes | Yes |
| Pin, archive, trash, restore, duplicate | Yes | Yes |
| Starter templates and Markdown shortcuts | Yes | Yes |
| Share one note as Markdown | Yes | Yes |
| Export full-library JSON backup | Yes | Yes |
| Restore/merge full-library backup | Yes | Yes |
| Deterministic duplicate-ID handling during backup restore | Yes | Yes |
| Preserve malformed Android local JSON for manual recovery | N/A | Yes |
| Shared versioned backup schema with millisecond timestamps | Yes | Yes |

## Not implemented / not production-ready

- Scanning and OCR (audio transcription intentionally excluded)
- PDF import, annotation and export
- Client UI for version history and conflict-aware merge
- Client-side account sign-in, automatic cross-device sync worker and offline outbox
- Client-side end-to-end encryption, secure key storage and recovery
- Safe Folder with biometric unlock and password fallback
- macOS WidgetKit extension, share extensions, quick capture and menu-bar capture remain outstanding
- Accessibility/localization audit and automated UI/integration tests

## Backup and security notes

Backups use a shared versioned JSON envelope (`schemaVersion: 1`) with normalized field names and Unix-millisecond timestamps. Restore merges by stable note ID and prefers the newest `updatedAtMs` record. The macOS importer also accepts its older JSON-array backup format. Android imports the shared envelope.

Backups and local note stores are plaintext. Store backups privately. Do not store passwords, financial secrets or private documents in this prototype. Safe Folder and cloud sync are not implemented. Markdown shortcuts insert syntax into plain text; they are not a full rendered editor.


## Reliability improvements in latest commits

- Android now exposes existing user folders as quick-filter chips and supports confirmed permanent deletion from Recently Deleted.
- Android backup restore deterministically selects the newest record when an imported backup repeats a note ID.
- Android preserves malformed local JSON in a separate recovery preference before the library is edited, rather than silently discarding the only copy.
- macOS rejects backup envelopes with malformed note identifiers instead of generating replacement IDs, and trashing a note now updates its modification timestamp.
- macOS now preserves an unreadable existing notes file rather than treating it as an empty library and overwriting it with a welcome note.
- Android switches to a single-pane note editor on narrow screens and retains a clear route back to the note list.

These changes have been committed, but CI is still required to confirm compilation on both platforms.

## Cloud sync infrastructure added

The Supabase migration `20261118000100_folio_encrypted_sync.sql` now defines per-user encrypted note envelopes, append-only revision history, attachment metadata, a private encrypted-attachment bucket, and row-level security policies. See [SYNC_SETUP.md](SYNC_SETUP.md).

**Important:** The migrations are backend groundwork only. Neither native client currently performs Supabase authentication, client-side encryption, background sync, or conflict resolution. Do not upload private notes to this schema using plaintext payloads.


## Latest implementation pass

- macOS and Android editors now include a Markdown preview mode alongside source editing. The macOS preview uses Foundation's Markdown parser; Android currently renders headings, quotes and list markers with native Compose typography. This is a useful preview, not yet a complete CommonMark/GFM editor.
- Android now includes a resizable home-screen widget showing the library count and most recently updated note preview. It refreshes when the local note library is saved.
- Drawing and handwriting are intentionally out of scope.

## Remaining release blockers

- **Cloud sync is not active yet.** The repository has Supabase encrypted storage/RLS and a hardened revision-checked write RPC, but no native sign-in, key setup/recovery, client encryption, pull/push worker, offline queue or conflict UI. The Supabase project URL and publishable key must be configured and migrations deployed before live sync can be tested. Never use a service-role key in either app.
- **Document management is partial.** Android notes can attach PDF, image, text and DOCX documents through the system document picker, reopen them and remove links. Attachments remain external content URIs with persisted read permission; they are not copied into Folio, included in JSON backups, available on macOS, or synchronized. A complete cross-platform document library still needs app-managed storage, rename, backup semantics, preview/share polish and encrypted sync.
- macOS WidgetKit needs a Widget Extension target; the current macOS package is a Swift Package executable, not an Xcode project with an extension target.


## Additional library controls

- Android now offers interactive sorting by recently updated, oldest first, title A–Z, and title Z–A. Pinned notes remain grouped ahead of unpinned notes in every sort mode.

## Peer-to-peer sync direction

Folio's target sync architecture is device-to-device: macOS and Android are equal peers, with automatic discovery and a sync attempt whenever the app opens. Same-network connections are the default; remote sync can use a private VPN managed by the user. No external cloud note-storage service is required. The protocol, pairing, encryption, offline queue, conflict, tombstone, and attachment requirements are specified in [P2P_SYNC_PROTOCOL.md](P2P_SYNC_PROTOCOL.md). This is a design contract only; native peer sync is not yet implemented and must not be presented as working.
