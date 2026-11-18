# Folio architecture

## Product principles

1. Native experiences: SwiftUI on macOS and Jetpack Compose on Android.
2. One canonical note/document schema across both clients.
3. Offline-first: local edits are durable before network sync begins.
4. Privacy by design: vault data is client-encrypted; the server stores opaque ciphertext.
5. Feature honesty: a module is complete only after implementation and automated/manual verification.

## Shared document contract

A document has a stable UUID, title, rich-text document body (versioned JSON), optional Markdown export, folder ID, tags, pinned/archived flags, created/updated timestamps, and a monotonically increasing local revision. Attachments are separate encrypted blobs referenced by document ID and content hash.

Rich text is represented as versioned structured JSON rather than platform-specific attributed strings. Clients may render platform-native editing controls while preserving the same portable document representation. Markdown import/export is an interchange format, not the canonical storage format.

## Synchronisation

- Every local mutation is written to the local database and an outbox.
- A sync worker uploads encrypted document envelopes with revision and parent revision metadata.
- The server returns remote changes after the last server cursor.
- Non-overlapping changes can be merged; overlapping edits must create a conflict copy until a deterministic merge is implemented and tested.
- Attachments use resumable upload/download and content hashes.
- Sync must never silently discard a local or remote revision.

The SQL migration establishes per-user row-level security and ciphertext columns. A production sync service still needs authenticated client integration, cursor pagination, retry/backoff, attachment storage, and conflict handling.

## Safe Folder

The vault is a separate security domain. Vault content and attachment bytes must be encrypted on-device before upload. A random vault data-encryption key is wrapped by a key-encryption key derived using a password KDF and, where supported, protected by the OS keystore/keychain with biometric-gated access. Password recovery must be explicit and must not be silently bypassed by biometrics. Biometrics are device-local; biometric templates are never stored or synced by Folio.

## Platform responsibilities

- macOS: SwiftUI navigation, AppKit integration where needed for advanced text editing, LocalAuthentication, Keychain, file import/export, Quick Note, menu bar and multi-window support.
- Android: Compose UI, Room persistence, BiometricPrompt, Android Keystore, WorkManager sync, share target, widgets and camera/document capture.
- Backend: authentication, encrypted payload relay, revision metadata, attachment metadata and per-user access control. It must not receive plaintext vault keys.
