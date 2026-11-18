# Folio security requirements

## Safe Folder authentication

The intended unlock flow is biometric-first with an in-app password fallback. On Android, use BiometricPrompt with a Keystore key requiring user authentication. On macOS, use LocalAuthentication and Keychain access control with Touch ID where available. The password is an app-level recovery path; OS login credentials may still be required by the operating system after reboot or policy changes.

## Encryption design

- Use vetted platform cryptography APIs and authenticated encryption (AES-GCM or ChaCha20-Poly1305 where supported).
- Generate random data-encryption keys; never hard-code keys.
- Derive password wrapping keys with Argon2id or PBKDF2 using unique random salts and calibrated parameters.
- Store only wrapped keys, salts, nonces, ciphertext and versioned KDF parameters.
- Keep key material in Keychain/Android Keystore when possible.
- Never store plaintext vault data in logs, crash reports, analytics, notification previews, search indexes, temporary exports, or server-side AI prompts.
- Clear in-memory vault keys on lock/background timeout as far as platform APIs permit.
- Biometrics authenticate local key use; biometric templates never leave the OS.

## Recovery

The user must explicitly configure a recovery method. If all devices and recovery material are lost, end-to-end encrypted data may be unrecoverable. Do not add a server-side master key or hidden password reset.

## Backend

Enable RLS on every user table. Clients must use publishable/anon credentials only. Service-role credentials belong only in server-side secrets. Never commit secrets to Git.

## Before release

Perform threat modelling, dependency review, key lifecycle review, backup/restore testing, offline and conflict testing, and an independent security review. The present repository is a development foundation, not a security certification.
