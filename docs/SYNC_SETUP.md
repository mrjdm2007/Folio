# Folio cloud sync setup and protocol

## Current state

The database migration in `supabase/migrations/20261118000100_folio_encrypted_sync.sql` creates the per-user encrypted-note, immutable revision-history, and private attachment metadata/storage schema. Row-level security restricts every record and object to its authenticated owner.

**This is backend infrastructure, not a claim that client sync is finished.** The current macOS and Android app builds do not yet authenticate to Supabase, encrypt local notes, run a sync queue, or expose account settings. Do not treat the feature as enabled until those client modules are shipped and tested.

## Provisioning

1. Create a Supabase project.
2. Apply migrations with `supabase db push` (or run the migration in the SQL editor).
3. Configure the project URL and publishable/anon key in each app's build configuration. Never put a service-role key in either client.
4. Enable email/password authentication and configure email confirmation / redirect URLs.
5. Keep the encrypted attachment bucket private. The migration sets a 50 MiB per-object limit.

## Client sync contract

A sync client must:

- Sign in through Supabase Auth and keep refresh tokens in Keychain (macOS) or Android Keystore-backed encrypted storage.
- Generate a random per-account data key on-device. Wrap it with a password-derived key using a vetted KDF (Argon2id where available; otherwise calibrated PBKDF2-HMAC-SHA256) and a random salt. Keep the unwrapped key only in memory while unlocked.
- Encrypt each serialized note and attachment locally using authenticated encryption (AES-256-GCM or ChaCha20-Poly1305). Generate a unique nonce for every encryption. The server receives ciphertext, nonce, key version, and a content hash, never plaintext or key material.
- Persist local edits and an outbox transactionally before attempting network work.
- Upload using optimistic concurrency: send the expected parent revision. If the remote current revision differs, download both encrypted versions and create a visible conflict copy; never silently overwrite either version.
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

Until every item above is complete, use local notes and JSON backups for important data. The schema alone does not provide end-to-end encryption or cross-device sync.
