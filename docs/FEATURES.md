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
- Automatic LAN discovery, a durable revision outbox, attachment replication and background retry
- Certificate-based mutual TLS / forward secrecy, device revocation and key rotation
- Robust common-ancestor conflict resolution and full two-device integration tests
- Safe Folder with biometric unlock and password fallback
- macOS WidgetKit extension, share extensions, quick capture and menu-bar capture remain outstanding
- Accessibility/localization audit and automated UI/integration tests

## Backup and security notes

Backups use a shared versioned JSON envelope (`schemaVersion: 1`) with normalized field names and Unix-millisecond timestamps. Restore merges by stable note ID and prefers the newest `updatedAtMs` record. The macOS importer also accepts its older JSON-array backup format. Android imports the shared envelope.

Backups and local note stores are plaintext. Store backups privately. Do not store passwords, financial secrets or private documents in this prototype. Safe Folder is not implemented. Markdown shortcuts insert syntax into plain text; they are not a full rendered editor. The first peer-sync pass encrypts network payloads, but does not encrypt the local note database.


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

- **Peer sync is in its first release pass.** It transfers notes and metadata over an encrypted direct connection, but still needs discovery, stronger transport identity/forward secrecy, attachment/history replication, durable revision tracking and independent security testing. Supabase is dormant and not used for this peer path.
- **Document management is partial.** Android notes can attach PDF, image, text and DOCX documents through the system document picker, reopen them and remove links. Attachments remain external content URIs with persisted read permission; they are not copied into Folio, included in JSON backups, available on macOS, or synchronized. A complete cross-platform document library still needs app-managed storage, rename, backup semantics, preview/share polish and encrypted sync.
- macOS WidgetKit needs a Widget Extension target; the current macOS package is a Swift Package executable, not an Xcode project with an extension target.


## Additional library controls

- Android now offers interactive sorting by recently updated, oldest first, title A–Z, and title Z–A. Pinned notes remain grouped ahead of unpinned notes in every sort mode.

## Peer-to-peer sync — first implementation

- Both clients can listen and initiate a direct TCP connection on port 47831.
- Pairing uses a high-entropy shared key copied out of band. Android stores it in Android Keystore-backed encrypted preferences; macOS stores it in Keychain.
- Each framed request and response is protected with AES-256-GCM; the key is derived from the shared secret using PBKDF2-HMAC-SHA256 with a fresh salt.
- Enter the other device's IP address once. The apps save it and attempt sync on app open; both also provide Sync Now.
- Note content and metadata replicate in both directions. Divergent versions are retained as deterministic copies in the Sync Conflicts folder.
- No external cloud note-storage service is involved.

This is a first usable direct-sync pass, not the complete release protocol. It currently requires manual IP entry and does not replicate attachments or version history. It has no certificate-based mutual TLS/forward secrecy, automatic LAN discovery, durable revision outbox, background retry while closed, device revocation, or audited two-device test suite. See [P2P_SYNC_PROTOCOL.md](P2P_SYNC_PROTOCOL.md) for the exact boundaries and security caveats.


## Library improvements (latest branch update)

- macOS and Android both provide note sorting by recent update, oldest first, and title order while keeping pinned notes grouped first.
- Android stores up to 50 throttled note snapshots per note and provides a version-history restore dialog.
- macOS stores up to 20 previous note revisions per note and exposes a Version History menu in the editor.
- These histories are local-only at present. They are not yet replicated between devices; P2P sync must include revision history and attachment manifests before it can be considered complete.


## Latest quality and interface pass

- Android note rows now use a selected-card treatment with a compact contextual actions menu instead of permanently displaying several destructive/action buttons on every row.
- Backup and restore actions are grouped with settings rather than consuming permanent space in the notes workspace.
- Android note editing now captures throttled local revision snapshots (up to 50 per note) and preserves the current version before a history restore.
- macOS note editor now exposes its existing local attachment store, including open and remove actions.
- macOS CI now packages a launchable `.app` bundle with a generated Folio icon and uploads a ZIP artifact. The app is not code-signed or notarized by this workflow.


## Tag filtering

- Android exposes horizontal tag chips beneath folder filters, with an All tags reset.
- macOS exposes a tag-filter menu above the note list.
- Tag filters combine with the selected folder/inbox, search query and sort mode; clearing the tag filter restores the full selected view.


## Expanded starter templates

- Both apps now offer Research Notes, Reading List, Weekly Planner and Lecture Revision templates alongside the existing Class Notes, Meeting, Daily Journal and Project Brief templates.
- Templates are ordinary editable Markdown notes, so their checklists work with the existing Tasks inbox and progress indicator.
