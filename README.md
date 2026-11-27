# Folio Web

Folio is a **web-only Progressive Web App**. The maintained product is the browser app; the Android and macOS application targets have been retired.

## What is included

- **Notes:** rich-text editing, headings, bold/italic/underline, links, images, pasted images, tables, checklists, code blocks, quotes, lists, tags, bookmarks, folders, duplicate, trash, search, print/PDF.
- **Automatic saving:** IndexedDB local persistence; no Save button required.
- **Migration and backup:** import the Android Folio JSON backup envelope, including its attachment map; export the complete local library and attachments to one JSON backup.
- **Study:** spaced-repetition flashcards, AI flashcard generation through a user-supplied Gemini API key, multiple-choice question bank and practice, exam calendar/checklists, focus timer, daily goal and study history.
- **Engineering tools:** SI and electronics unit conversion (V, A, Ω, F, H, power, frequency, etc.), graph plotting, saved graphs and a LaTeX formula library with KaTeX preview.
- **PDF uploads:** upload multiple PDFs, keep them in a local PDF library, view file size and upload date, download or delete files. No document viewer, OCR or in-app document opening.

- **PWA:** responsive phone/desktop layout, installable manifest, service worker and app-shell offline fallback.
- **Cloud sync:** optional Firebase email/password sign-in, Firestore note/data sync and Firebase Storage attachment sync. You bring your own Firebase project configuration.

## Local development

Requires Node.js 20 or newer.

```sh
npm install
npm run dev
npm run typecheck
npm run build
npm run preview
```

## Android Folio migration

In Settings → Import Folio backup, select the JSON backup created by the Android app. The web importer retains the shared data fields (notes, folders, tags, bookmarks, flashcards, questions, exams, study sessions, formulas, saved graphs and extracted document text) and restores attachment bytes into browser storage. After import, immediately export a fresh Folio Web backup as a safety copy.

Uploaded PDFs and the notes library are stored in the current browser profile. Clearing browser site data clears that local copy, so export backups regularly. Use Firebase sync below to keep a remote copy and move data between devices.

## Firebase cloud sync setup

1. Create a Firebase project and register a Web App.
2. Enable **Authentication → Email/Password**.
3. Create a **Cloud Firestore** database and a **Cloud Storage** bucket.
4. Deploy the rules in `firebase/firestore.rules` and `firebase/storage.rules`.
5. In Folio → Settings & backup, paste the Firebase Web App config JSON, then create an account or sign in and choose **Upload this library**.
6. On another device, enter the same Firebase config and account details and choose **Restore from cloud**.

The Firebase Web config is a public client identifier, not an admin credential. The included rules restrict each account to its own user path. Do not place service-account private keys in the browser. Firestore and Storage usage is subject to your Firebase plan and quotas. Large embedded images in rich-text HTML can exceed Firestore's document-size limit; keep very large images as attachments and maintain JSON backups.

## AI flashcards and notifications

AI flashcard generation requires a Gemini API key from Google AI Studio. The key is stored in this browser's local storage and sent directly to Google's Gemini API; use an appropriately restricted key and do not share it. Hourly topic reminders use the browser Notification API and run only while Folio remains open; they are not guaranteed when the browser or device is closed.

## Deployment

GitHub Actions runs TypeScript checks and a production Vite build, uploads a `folio-web-dist` artifact, and deploys the PWA to GitHub Pages when Pages is enabled for the repository. The expected project URL is:

`https://mrjdm2007.github.io/Folio/`

If the Pages deployment reports that Pages is not enabled, open Repository Settings → Pages and select **GitHub Actions** as the build and deployment source, then re-run the workflow.
