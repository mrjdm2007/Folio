# Folio cloud sync setup and protocol

## Current state

The database migrations create the per-user encrypted-note, immutable revision-history, and private attachment metadata/storage schema. The `folio_push_note` RPC in `20261118000200_folio_atomic_sync_push.sql` performs an atomic compare-and-write against the expected revision and reports conflicts instead of overwriting them. The follow-up hardening migration `20261118000300_folio_sync_write_hardening.sql` revokes direct note/revision writes from authenticated clients and makes the RPC the sole note-envelope write path. It verifies the SHA-256 hash of ciphertext plus nonce. Row-level security restricts every record and object to its authenticated owner.

**This is backend infrastructure, not a claim that client sync is finished.** The current macOS and Android app builds do not yet authenticate to Supabase, encrypt local notes, run a sync queue, or expose account settings. Do not treat the feature as enabled until those client modules are shipped and tested.

## Provisioning

1. Create a Supabase project.
2. Apply all migrations in timestamp order with `supabase db push` (or run each migration in timestamp order in the SQL editor), including the write-hardening migration.
3. Configure the project URL and publishable/anon key in each app's build configuration. Never put a service-role key in either client.
4. Enable email/password authentication and configure email confirmation / redirect URLs.
5. Keep the encrypted attachment bucket private. The migration sets a 50 MiB per-object limit.

## Client sync contract

A sync client must:

- Sign in through Supabase Auth and keep refresh tokens in Keychain (macOS) or Android Keystore-backed encrypted storage.
- Generate a random per-account data key on-device. Wrap it with a password-derived key using a vetted KDF (Argon2id where available; otherwise calibrated PBKDF2-HMAC-SHA256) and a random salt. Keep the unwrapped key only in memory while unlocked.
- Encrypt each serialized note and attachment locally using authenticated encryption (AES-256-GCM or ChaCha20-Poly1305). Generate a unique nonce for every encryption. The server receives ciphertext, nonce, key version, and a content hash, never plaintext or key material.
- Persist local edits and an outbox transactionally before attempting network work.
- Upload using the `folio_push_note` RPC with the expected parent revision. A `conflict` outcome means the server has a newer revision; fetch both encrypted versions and create a visible conflict copy. Never silently overwrite either version.
- Pull remote revisions using a stable cursor / updated-at plus ID ordering, and make retries idempotent.
- Treat trash as a tombstone so deletion propagates to other devices. Permanent deletion should be a separate explicit operation.
- Upload encrypted attachments to `folio-encrypted-attachments/<auth.uid()>/<attachment-id>` and verify the ciphertext hash after download.
- Pause sync when signed out, offline, or when the encryption key is locked. Never upload plaintext as a fallback.

## Conflict behaviour

For the same note ID, if local and remote have diverged from a shared parent revision, preserve both. The UI should offer the user the local copy, remote copy, and a manual merge into a new revision. A timestamp alone is not a safe conflict resolver because device clocks can differ.

## Security checklist before enabling sync

- [ ] Client-side authenticated encryption and key wrapping reviewed.
- [ ] Auth refresh tokens stored in platform secure storage.
- [ ] RLS tested using two separate accounts and anonymous requests.
- [ ] Offline queue, retry/backoff, duplicate delivery, and pagination tested.
- [ ] Conflict and tombstone propagation tested on two devices.
- [ ] Attachment size/type validation and hash verification tested.
- [ ] No service-role key, plaintext note, or encryption key in logs or client bundles.
- [ ] Account deletion and key recovery behaviour documented.

Until every item above is complete, use local notes and JSON backups for important data. The schema and hardened RPC alone do not provide end-to-end encryption or cross-device sync. No native app currently calls Supabase or encrypts its local note data for sync. Do not enable sync or upload note content until client cryptography, auth, queueing and conflict handling are implemented and tested.
