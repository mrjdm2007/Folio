# Folio

Folio is a native macOS + Android notes and knowledge workspace with a shared document model, offline-first storage, encrypted synchronisation, and a separately protected Safe Folder.

## Repository layout

- `macOS/` — SwiftUI macOS application.
- `Android/` — Kotlin + Jetpack Compose Android application.
- `Shared/` — cross-platform document and sync contracts.
- `supabase/` — PostgreSQL schema, row-level security, and storage policies.
- `docs/` — architecture, security, feature matrix, and build instructions.

## Current implementation status

This repository bootstrap includes runnable native app foundations, local note creation/editing/search/pinning, a shared encrypted-payload sync schema, and CI/build configuration. Advanced modules (PDF markup, audio transcription, handwriting recognition, AI, canvas, graph visualisation, full conflict-free sync, and production key management) are explicitly tracked in the feature matrix and are not represented as completed until implemented and tested.

## Build

### macOS
Open `macOS/Package.swift` in Xcode or run `cd macOS && swift build` on a Mac with a current Swift toolchain.

### Android
Open `Android/` in Android Studio (JDK 17+, Android SDK 35+). Android Studio will generate/use the Gradle wrapper for the local environment.

### Backend
Apply SQL migrations in `supabase/migrations/` to a Supabase project. Configure authentication and storage policies before enabling sync. Never commit service-role keys or encryption secrets.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/FEATURES.md](docs/FEATURES.md), and [docs/SECURITY.md](docs/SECURITY.md).
