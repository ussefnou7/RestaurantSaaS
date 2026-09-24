# Media attachments — API contract (D128)

> **Verified against code:** backend working tree on `85d9b7a`, admin-web working tree on
> `fa426b7`, 2026-09-21. Migration `V63__media_attachments.sql`. Every claim here traces to
> `media/MediaController.java`, `media/MediaService.java` or `media/enums/MediaPurpose.java`.

The capability is generic. It ships wired to **two** owners — products and employees — and adding
a third is one `MediaOwnerType` value, one `MediaPurpose` value, one `MediaOwnerResolver` bean and
one migration widening two CHECK constraints.

---

## Purposes — the only place a per-purpose rule lives

| Purpose | Owner | View permission | Manage permission | Cardinality | Types | Max | Renditions |
|---|---|---|---|---|---|---|---|
| `PRODUCT_IMAGE` | `PRODUCT` | `PRODUCTS_VIEW` | `PRODUCTS_UPDATE` | single | jpeg, png, webp | 10 MB | `ORIGINAL`, `LARGE` 1600, `MEDIUM` 800, `THUMB` 200 |
| `EMPLOYEE_PHOTO` | `EMPLOYEE` | `HR_EMPLOYEES_VIEW` | `HR_EMPLOYEES_UPDATE` | single | jpeg, png, webp | 5 MB | `ORIGINAL`, `MEDIUM` 400, `THUMB` 96 |

Sizes are a **longest edge**. Aspect ratio is preserved and an image is **never upscaled** — a
60px avatar stores three renditions of identical dimensions. There is **no server-side cropping**;
the UI squares an avatar with `object-fit`.

**There is no `MEDIA_UPLOAD` permission and none is to be added.** Attaching to a record requires
that record's own permission. A generic upload permission would let anyone holding it attach a
file to any record in the system.

---

## Endpoints

All under `/api/media`. Every one requires authentication; the *specific* permission is resolved
from the purpose at request time by `MediaService`, not from a `@PreAuthorize` string — the
permission is request data and an annotation cannot express it.

### `POST /api/media/uploads` → `201 MediaResponse`

`multipart/form-data`. Query params `purpose`, `ownerId`; part `file`. Optional `X-User-Id` header
for audit.

Requires the purpose's **manage** permission.

Uploading a second file to a single-valued purpose **replaces** — it does not error. The
replacement and the old file's destruction happen in one transaction.

### `DELETE /api/media/links/{linkId}` → `204`

Requires the purpose's **manage** permission, and `MediaOwnerResolver.isMutable`. **Permanent** —
no detached state, no version history, nothing to restore into.

### `GET /api/media/owners/{ownerType}/{ownerId}` → `MediaResponse[]`

Every attachment on one record. Requires the **view** permission of each purpose present.

### `GET /api/media/links?purpose=&ownerIds=1,2,3` → `MediaResponse[]`

One purpose across many owners, for list screens. Requires that purpose's **view** permission.
Omitting this endpoint and asking per row is N+1 over a table that is polymorphic and therefore
unjoinable from the owner's own query.

### `GET /api/media/{mediaFileId}/{variant}` → the bytes

`variant` is lowercase (`original` | `large` | `medium` | `thumb`).

Requires the **view** permission of any purpose the file is linked under. A file with no link is
unreachable rather than public: the permission comes from the link.

Response headers:

```
ETag:                    "<checksum_sha256>-<VARIANT>"
Cache-Control:           max-age=31536000, private, immutable
Content-Type:            <sniffed at upload, not what the client claimed>
X-Content-Type-Options:  nosniff
```

A matching `If-None-Match` answers `304`. `immutable` is safe because content is never rewritten
under an existing key — a replacement is a new uuid and therefore a new key. **Cache invalidation
is not needed and must not be implemented;** that property is what a CDN will later depend on.

---

## `MediaResponse`

```jsonc
{
  "linkId": 12,
  "mediaFileId": 7,
  "ownerType": "PRODUCT",
  "ownerId": 42,
  "purpose": "PRODUCT_IMAGE",
  "sortOrder": 0,
  "originalFilename": "margherita.jpg",   // display only; never appears in a storage key
  "contentType": "image/jpeg",
  "sizeBytes": 184322,
  "width": 2000,
  "height": 1500,
  "variants": [
    { "variant": "ORIGINAL", "width": 2000, "height": 1500, "sizeBytes": 184322,
      "url": "/api/media/7/original" },
    { "variant": "THUMB", "width": 200, "height": 150, "sizeBytes": 4102,
      "url": "/api/media/7/thumb" }
  ]
}
```

**`url` is built at read time and must never be persisted.** Storing it turns a storage-provider
change into a data migration across every row that copied it.

---

## Embedding images in another module's projection

`GET /api/menu` returns each product's image inline, so a POS grid does not make one media request
per tile. The field is `image`, shaped as `MediaSummaryResponse`:

```jsonc
{
  "id": 21, "name": "Cheese Pizza", "type": "PARENT",
  "image": {
    "mediaFileId": 7,
    "variants": [
      { "variant": "THUMB",  "width": 200,  "height": 150,  "url": "/api/media/7/thumb" },
      { "variant": "MEDIUM", "width": 800,  "height": 600,  "url": "/api/media/7/medium" }
    ]
  },
  "variants": [
    { "id": 24, "variantLabel": "Large", "sellingPrice": 140.00, "image": { … } },
    { "id": 22, "variantLabel": "Small", "sellingPrice": 70.00,  "image": null }
  ]
}
```

- `MenuItemResponse` is `NON_NULL`, so a product with no image **omits** the key. `MenuVariantResponse`
  is not, so a variant with no image sends `"image": null`. That asymmetry is pre-existing DTO
  config, not a decision about media.
- **A variant carries its own image** — it is a product row of its own. Falling back to the
  parent's image is the consumer's call; the projection does not do it for you.
- **Add-ons carry no image**, deliberately: they render as a name and a price, not a tile.
- Cost is **two extra queries for the whole menu**, whether one product has an image or two
  hundred. `MenuServiceTest` verifies `summariesForOwners` is called exactly once, and
  `MenuReadModelIntegrationTest` pins the total statement count — a loop would pass neither.

Other modules embed the same way: `MediaService.summariesForOwners(tenantId, purpose, ownerIds)`.
Do **not** call `listForOwner` per row.

---

## Error codes

Every one is renderable by `translateApiError`; strings live in `i18n/locales/{en,ar}/media.ts`.

| Code | Status | When |
|---|---|---|
| `MEDIA_NOT_FOUND` | 404 | No such link/file **for this tenant** — a cross-tenant id is indistinguishable from a missing one |
| `MEDIA_VARIANT_NOT_FOUND` | 404 | Unknown variant name, or a rendition this file does not have |
| `MEDIA_OWNER_NOT_FOUND` | 404 | `MediaOwnerResolver.exists` said no, for the **request's** tenant |
| `MEDIA_OWNER_IMMUTABLE` | 409 | Owner is final; attachments are add-only |
| `MEDIA_FILE_EMPTY` | 400 | No bytes |
| `MEDIA_UNSUPPORTED_CONTENT_TYPE` | 400 | Sniffed type not allowed by the purpose. Params carry `detectedContentType` + `allowedContentTypes` |
| `MEDIA_HEIC_NOT_SUPPORTED` | 400 | Named separately so the UI can tell a phone user what to change |
| `MEDIA_FILE_TOO_LARGE` | 413 | Over the purpose ceiling. Params carry `maxSizeBytes` |
| `MEDIA_IMAGE_UNREADABLE` | 400 | Passed the type check and still would not decode |
| `MEDIA_STORAGE_FAILURE` | 500 | The storage layer failed |

---

## Two orderings carry the whole correctness story, and they are opposites

- **Upload: bytes first, commit second.** The worst outcome is a stored object with no row —
  invisible, harmless, sweepable. The reverse produces a row pointing at nothing, which renders as
  a broken image and cannot be swept, because from the database's side it looks correct.
- **Delete: commit first, bytes second.** Storage keys go to `media_deletion_queue` in the same
  transaction as the row deletion; `MediaDeletionScheduler` removes the objects afterwards.
  Deleting bytes inside the transaction means a rollback destroys a file whose row came back.

---

## Frontend

`<img src>` cannot send an `Authorization` header, and every media read is permission-gated. So:

- Use **`<MediaImage url={...} />`**, which fetches the blob and renders an object URL. A raw
  `<img src={variant.url}>` anywhere in the app is a 401 that renders as a broken image.
- Use **`<ImageUploader purpose ownerType ownerId />`** for the single-image control. It loads,
  uploads, replaces and removes, and confirms before a permanent delete.
- Network cost is unchanged: `immutable` + a year's `max-age` is honoured by the browser HTTP
  cache for `fetch` exactly as for `<img>`.
- Object URLs pin their blobs, so `mediaService` caps the cache at 150 entries and revokes on
  eviction. Call `releaseMediaObjectUrl(oldUrl)` after a replace or remove.

---

## Operational requirements — tell whoever deploys this

1. **`app.media.storage-root` must be an absolute path outside the deployed war.** A path inside
   the servlet container is erased by the next deployment, and every image goes with it.
2. **`pg_dump` is no longer a complete backup.** Media is not in the database. The storage root
   needs its own backup.
3. **The multipart ceiling is two limits.** `spring.servlet.multipart.max-file-size` is set to
   16 MB, but any reverse proxy has its own body cap (nginx `client_max_body_size` defaults to
   1 MB) and rejects the request before Spring sees it — from a layer that produces no `errorCode`
   for `translateApiError` to render.

## Known gaps

- **EXIF orientation is not applied.** A portrait phone photo displays unrotated.
- **The orphan sweep is not built.** `StorageService.list` exists for it and has no other caller.
- **Link→owner reconciliation is not built**, and it is now known to be needed: products have a
  hard-delete path, so deleting one orphans its `media_link` row and leaks its bytes.
