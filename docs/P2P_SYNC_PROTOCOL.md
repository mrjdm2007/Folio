# Folio peer-to-peer sync protocol

## Product decision

Folio is local-first. The macOS and Android installations are equal peers: either device can initiate a connection, offer its local change log, and receive the other peer's changes. Folio does not require a hosted note database or external cloud-storage account.

The first transport is the same trusted Wi-Fi/LAN. Remote use is supported when the user explicitly connects both devices through a private VPN (for example, a user-managed Tailscale network). Folio must not expose an unauthenticated listener to the public internet or silently create a third-party relay dependency.

## Required app-open behaviour

1. Load the local library immediately; never block the editor on network discovery.
2. On every foreground/app-open transition, start a bounded peer-discovery attempt.
3. If a paired peer is found, authenticate it using the stored pairing identity and pinned public-key fingerprint.
4. Exchange protocol version, device identity, per-device sync cursor, and capability list.
5. Exchange change manifests first. Transfer only missing revisions and attachment chunks.
6. Apply remote changes transactionally, persist the new cursor, then drain the local outbox.
7. Update last-successful-sync only after all acknowledged batches are durable on both sides.
8. If no peer is found, authentication fails, or the network drops, leave local data untouched and retain the outbox for the next foreground attempt.

A failed or unavailable peer must never prevent opening or editing notes. The app must expose a manual Sync Now action and a concise status: Checking, Syncing, Up to date, Device unavailable, or Needs attention.

## Pairing and transport security

- Pairing is initiated by a user on both devices and confirmed using a short-lived QR code or comparison code.
- The QR payload contains a protocol version, ephemeral connection endpoint, random pairing challenge, and public-key fingerprint; it must not contain note content, passwords, or long-lived bearer credentials.
- Establish TLS 1.3 with mutual peer authentication. Pin the paired peer's public-key fingerprint and reject unknown or changed identities until the user explicitly re-pairs.
- Use a fresh session key and authenticated encryption for every session. Never accept a peer solely because it is on the same Wi-Fi.
- Bind LAN listeners only to the local network interface, require pairing, rate-limit handshakes, and close idle sessions.
- Remote connections are supported through the user's private VPN address. Do not implement public port forwarding, UPnP, or an unauthenticated internet listener.
- No note content, attachment content, encryption keys, or passwords may be sent to a hosted service.

## Replication model

Each note has a stable UUID. Every local mutation creates an immutable change record with:
- note ID;
- monotonically increasing per-note revision;
- parent revision(s);
- device ID and mutation ID;
- updated timestamp for display only;
- tombstone flag;
- encrypted payload and authenticated metadata.

Timestamps are not conflict-resolution authority. Device clocks can drift. Mutation IDs make retries idempotent. A peer acknowledges a change only after it has committed it to durable local storage.

When two edits descend from the same parent revision, preserve both branches. Create a visible conflict copy with links to both versions; never silently use last-write-wins. A manual merge creates a new revision whose parents include the conflicting heads.

Trash is replicated as a tombstone. Permanent deletion is a separate explicit action and must be confirmed. Tombstones remain until every paired device has acknowledged them or the user explicitly removes an offline device from the device list.

## Offline outbox and retry

- Persist local note and attachment mutations to the local store and outbox in one transaction.
- Retry with bounded exponential backoff and jitter while the app is active.
- Resume an interrupted transfer from the last acknowledged chunk.
- Do not duplicate changes when a batch is retried.
- Keep the outbox while the peer is unavailable, the app is closed, or the device is offline.
- On app open, perform one discovery/sync attempt immediately; additional attempts occur only on meaningful network/foreground events or an explicit user action.

## Attachments

Attachments are app-managed files, not transient provider URIs. Encrypt each attachment before transfer, hash the ciphertext, and transfer in bounded chunks with resumable offsets. Verify the final hash before atomically publishing the attachment in the local index. Enforce file-size and content-type limits. A note is not considered fully synchronised until its attachment manifest and required blobs are acknowledged.

## Storage and key management

- Keep each device's local database independent and usable offline.
- Store the paired-device private identity in macOS Keychain / Android Keystore-backed storage.
- Keep note encryption keys separate from transport keys.
- Use vetted platform cryptography; never implement primitives manually.
- The sync protocol must support key rotation and device revocation.
- Backups must include an explicit, documented policy for encrypted attachments and key recovery.

## Compatibility and safety

Peers negotiate a protocol version. Unknown mandatory capabilities cause a clear incompatibility status; they must not trigger lossy downgrade. Validate every message length, identifier, revision, chunk offset, hash, and attachment path. Treat all peer input as untrusted. Avoid logging payloads or secrets.

## Release acceptance tests

- Pairing succeeds only after explicit confirmation on both devices.
- An unpaired device on the same Wi-Fi cannot read or modify notes.
- App launch with peer online synchronises both directions without a manual tap.
- App launch with peer offline opens immediately and preserves queued changes.
- Reopening after an interrupted transfer resumes without duplicates or corruption.
- Simultaneous edits on both devices create a recoverable conflict copy.
- Trash and restore propagate; permanent deletion is explicit.
- Attachments transfer, verify, open, and survive app restart on both platforms.
- Revoked devices cannot reconnect.
- No external storage account, hosted database, or public relay is required.
- Tests cover malformed messages, replayed mutation IDs, stale revisions, oversized payloads, and changed peer fingerprints.

## Implementation status

This document is the implementation contract, not evidence that the protocol is already shipped. The current clients still use local JSON/preference storage and do not yet implement peer discovery, pairing, authenticated transport, change logs, transactional outboxes, or bidirectional sync. Do not advertise sync as available until both native clients implement the contract and the acceptance tests pass.
