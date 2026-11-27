# Folio Web

Folio is moving to a **web-only Progressive Web App**. The browser/PWA is the sole maintained client; Android and macOS app builds are discontinued.

## Development

- Node.js 20+
- `npm install`
- `npm run dev`
- `npm run typecheck`
- `npm run build`

## Web foundation

Responsive workspace; TipTap rich-text editor; headings, lists, quotes, code, links and inline images; notes, folders, tags, bookmarks, search and trash; IndexedDB autosave; import/export of the existing Folio JSON data envelope; PWA manifest and service-worker app-shell caching; flashcards and a focus timer.

## Migration status

The existing Android data model includes notes/topics, folders, blocks, tags, bookmarks, trash, flashcards, questions, exams, study sessions, formulas, saved graphs and extracted PDF text. Import preserves unknown/shared data fields, even where a web screen is not yet implemented. Existing backup attachment payloads are not yet unpacked into the browser attachment store. Browser data is device/browser-local; authenticated cloud sync is a separate unfinished milestone. Export backups regularly.

## Deployment

GitHub Actions type-checks and builds the static site into `dist/` on pushes and pull requests. Configure a static host to publish that artifact.
