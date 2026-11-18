# Shared document contract v1

Both clients must serialize the same logical document fields:

```json
{
  "schemaVersion": 1,
  "id": "UUID",
  "title": "string",
  "blocks": [],
  "folderId": "UUID|null",
  "tags": ["string"],
  "isPinned": false,
  "isArchived": false,
  "isVault": false,
  "createdAt": "ISO-8601",
  "updatedAt": "ISO-8601",
  "revision": 1
}
```

Block types planned for v1: paragraph, heading, bulletList, numberedList, checklist, quote, code, table, image, file, audio, drawing, math, and embed. Unknown block types must be preserved during round-trip serialization so older clients do not erase newer content.
