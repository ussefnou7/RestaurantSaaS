package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.CommonErrorCode;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.media.dto.MediaResponse;
import com.smart.restaurant_saas.media.dto.MediaSummaryResponse;
import com.smart.restaurant_saas.media.enums.DerivativeSet;
import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.enums.MediaPurpose;
import com.smart.restaurant_saas.media.enums.MediaVariantType;
import com.smart.restaurant_saas.media.image.ContentTypeSniffer;
import com.smart.restaurant_saas.media.image.ImageTranscoder;
import com.smart.restaurant_saas.media.spi.MediaOwnerResolver;
import com.smart.restaurant_saas.media.storage.StorageKeys;
import com.smart.restaurant_saas.media.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The media module's public behaviour: attach a file, remove one, list what an owner has, and open
 * a rendition for streaming.
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional} at the class level. The upload path
 * writes bytes before any row exists and the delete path writes rows before any byte is removed;
 * both orderings are the correctness story, and a class-level annotation would quietly pull the
 * storage calls inside a transaction where they do the opposite of what is intended.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaFileRepository mediaFileRepository;
    private final MediaVariantRepository mediaVariantRepository;
    private final MediaLinkRepository mediaLinkRepository;
    private final MediaCommitService commitService;
    private final MediaOwnerResolverRegistry resolverRegistry;
    private final SecurityService securityService;
    private final ImageTranscoder imageTranscoder;
    private final ContentTypeSniffer contentTypeSniffer;
    private final StorageService storageService;
    private final MediaMapper mediaMapper;

    /** A rendition ready to stream, with everything the response headers need. */
    public record StreamedVariant(Resource resource, String contentType, Long sizeBytes,
                                  String checksumSha256) {}

    /**
     * Attaches a file to an owner.
     *
     * <p><strong>Bytes first, commit second.</strong> The worst outcome of this ordering is a
     * stored object with no row — invisible, harmless, and reclaimable by the orphan sweep. The
     * reverse ordering produces a row pointing at nothing, which renders as a broken image and
     * cannot be swept, because from the database's side it looks correct.
     */
    public MediaResponse upload(Long tenantId, Long userId, MediaPurpose purpose, Long ownerId,
                                MultipartFile upload) {
        requireManage(purpose);
        requireOwnerExists(tenantId, purpose.getOwnerType(), ownerId);

        byte[] original = readBytes(upload);
        String contentType = validateContentType(purpose, original, upload);
        validateSize(purpose, original.length);

        ImageTranscoder.Transcoded transcoded =
                imageTranscoder.transcode(original, purpose.getDerivativeSet());

        String uuid = UUID.randomUUID().toString();
        MediaFile file = newMediaFile(tenantId, userId, upload, contentType, original, transcoded);
        List<MediaVariantRow> variants =
                storeAllBytes(tenantId, purpose, uuid, contentType, original, transcoded);

        MediaLink link = newLink(tenantId, userId, purpose, ownerId);
        MediaLink saved = commitService.commitUpload(
                new MediaCommitService.PendingUpload(file, variants, link));
        return mediaMapper.toResponse(saved, file, variants);
    }

    /**
     * Removes an attachment. The bytes go to the deletion queue, not to the filesystem — see
     * {@link MediaCommitService#deleteLink}.
     *
     * <p>Deletion is permanent. There is no detached state and no version history: an unreachable
     * file that no screen lists and no endpoint returns is not history, and personal data retained
     * with no purpose and no path to it is a liability rather than a feature.
     */
    public void delete(Long tenantId, Long linkId) {
        MediaLink link = mediaLinkRepository.findByIdAndTenantId(linkId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(MediaErrorCode.MEDIA_NOT_FOUND,
                        "No media link %d for tenant %d".formatted(linkId, tenantId),
                        ErrorParams.of("linkId", linkId)));

        requireManage(link.getPurpose());

        MediaOwnerResolver resolver = resolverRegistry.require(link.getOwnerType());
        if (!resolver.isMutable(tenantId, link.getOwnerId())) {
            throw new ValidationException(MediaErrorCode.MEDIA_OWNER_IMMUTABLE,
                    "Owner %s %d is final; attachments on it are add-only"
                            .formatted(link.getOwnerType(), link.getOwnerId()),
                    ErrorParams.of("ownerType", link.getOwnerType().name(),
                            "ownerId", link.getOwnerId()));
        }
        commitService.deleteLink(link);
    }

    @Transactional(readOnly = true)
    public List<MediaResponse> listForOwner(Long tenantId, MediaOwnerType ownerType, Long ownerId) {
        List<MediaLink> links = mediaLinkRepository
                .findByTenantIdAndOwnerTypeAndOwnerIdOrderBySortOrderAscIdAsc(tenantId, ownerType, ownerId);
        links.stream().map(MediaLink::getPurpose).distinct().forEach(this::requireView);
        return toResponses(links);
    }

    /**
     * Every owner's attachment for one purpose, in one pair of queries.
     *
     * <p>List screens need an image per row. Asking per row is N+1 against a table that is already
     * polymorphic and therefore unjoinable from the owner's own query.
     */
    @Transactional(readOnly = true)
    public List<MediaResponse> listForOwners(Long tenantId, MediaPurpose purpose, List<Long> ownerIds) {
        requireView(purpose);
        if (ownerIds == null || ownerIds.isEmpty()) {
            return List.of();
        }
        return toResponses(mediaLinkRepository
                .findByTenantIdAndPurposeAndOwnerIdInOrderBySortOrderAscIdAsc(tenantId, purpose, ownerIds));
    }

    /**
     * One purpose's attachment per owner, reduced to file id + renditions, in **two** queries
     * regardless of how many owners are asked for.
     *
     * <p>This is what other modules embed in their own read projections — the menu does, so a POS
     * tile arrives with its image instead of fetching one per product. It deliberately skips the
     * {@code media_file} row that {@link #listForOwners} loads: a projection needs the URLs, not
     * the filename, checksum and content type.
     *
     * @return owner id → its attachment; owners with none are absent rather than mapped to null
     */
    @Transactional(readOnly = true)
    public Map<Long, MediaSummaryResponse> summariesForOwners(
            Long tenantId, MediaPurpose purpose, List<Long> ownerIds) {
        return summariesForOwners(tenantId, purpose, ownerIds, null);
    }

    /**
     * As {@link #summariesForOwners(Long, MediaPurpose, List)}, but with one rendition's bytes
     * embedded in every summary.
     *
     * <p>For clients that cannot authenticate an image request. {@code /api/media/**} is
     * permission-gated and a browser sends no bearer token with an {@code <img src>}, so a
     * token-authenticated client has no way to render a url this projection hands it; the bytes
     * have to arrive in the body of the call it already made. It also turns what would be one
     * request per tile into none.
     *
     * <p>Still two queries for the rows, plus one storage read per distinct file. Those reads are
     * the cost of the feature and the reason {@code inlineVariant} should be the smallest
     * rendition a surface can display — at {@code FULL}'s sizes that is {@code THUMB}, roughly a
     * thousandth of {@code ORIGINAL}'s permitted upload.
     *
     * <p>A file missing the requested rendition, or whose bytes cannot be read, yields a summary
     * with urls and no {@code dataUri} rather than a failed menu. A tile that renders a
     * placeholder is a smaller problem than a POS that cannot list its products.
     *
     * @param inlineVariant rendition to embed, or null to embed nothing
     */
    @Transactional(readOnly = true)
    public Map<Long, MediaSummaryResponse> summariesForOwners(
            Long tenantId, MediaPurpose purpose, List<Long> ownerIds, MediaVariantType inlineVariant) {
        requireView(purpose);
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Map.of();
        }
        List<MediaLink> links = mediaLinkRepository
                .findByTenantIdAndPurposeAndOwnerIdInOrderBySortOrderAscIdAsc(tenantId, purpose, ownerIds);
        if (links.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<MediaVariantRow>> variantsByFile = mediaVariantRepository
                .findByMediaFileIdIn(links.stream().map(MediaLink::getMediaFileId).distinct().toList())
                .stream()
                .collect(Collectors.groupingBy(MediaVariantRow::getMediaFileId));

        Map<Long, MediaSummaryResponse> byOwner = new LinkedHashMap<>();
        for (MediaLink link : links) {
            // putIfAbsent, not put: every shipping purpose is single-valued so there is one link
            // per owner, but a multi-valued one would otherwise silently return its last
            // attachment. First-by-sort-order is the only defensible pick until a purpose grows a
            // "primary" concept, which is a product decision and not this method's to invent.
            if (byOwner.containsKey(link.getOwnerId())) {
                continue;
            }
            List<MediaVariantRow> rows = variantsByFile.getOrDefault(link.getMediaFileId(), List.of());
            byOwner.put(link.getOwnerId(), new MediaSummaryResponse(
                    link.getMediaFileId(),
                    mediaMapper.toVariantResponses(
                            link.getMediaFileId(), rows, readInline(rows, inlineVariant))));
        }
        return byOwner;
    }

    /** Bytes for the one rendition a projection wants inline, or empty when it wants none. */
    private Map<MediaVariantType, byte[]> readInline(
            List<MediaVariantRow> rows, MediaVariantType inlineVariant) {
        if (inlineVariant == null) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> row.getVariant() == inlineVariant)
                .findFirst()
                .map(row -> {
                    try (InputStream stream = storageService.get(row.getStorageKey()).getInputStream()) {
                        return Map.of(inlineVariant, stream.readAllBytes());
                    } catch (IOException | RuntimeException ex) {
                        // Degraded, not fatal — see the method comment above.
                        log.warn("Could not inline {} of media file {}: {}",
                                inlineVariant, row.getMediaFileId(), ex.toString());
                        return Map.<MediaVariantType, byte[]>of();
                    }
                })
                .orElseGet(Map::of);
    }

    /**
     * Opens one rendition for streaming, gated by the purposes the file is linked under.
     *
     * <p>A file with no link is unreachable rather than public: the permission comes from the link,
     * so the absence of one is the absence of any permission that could allow the read.
     */
    @Transactional(readOnly = true)
    public StreamedVariant openVariant(Long tenantId, Long mediaFileId, MediaVariantType variant) {
        MediaFile file = mediaFileRepository.findByIdAndTenantId(mediaFileId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(MediaErrorCode.MEDIA_NOT_FOUND,
                        "No media file %d for tenant %d".formatted(mediaFileId, tenantId),
                        ErrorParams.of("mediaFileId", mediaFileId)));

        Set<MediaPurpose> purposes = mediaLinkRepository
                .findByTenantIdAndMediaFileId(tenantId, mediaFileId).stream()
                .map(MediaLink::getPurpose)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (purposes.isEmpty()) {
            throw new ResourceNotFoundException(MediaErrorCode.MEDIA_NOT_FOUND,
                    "Media file %d has no link, so no purpose grants it".formatted(mediaFileId),
                    ErrorParams.of("mediaFileId", mediaFileId));
        }
        requireViewOfAny(purposes);

        MediaVariantRow row = mediaVariantRepository.findByMediaFileIdAndVariant(mediaFileId, variant)
                .orElseThrow(() -> new ResourceNotFoundException(MediaErrorCode.MEDIA_VARIANT_NOT_FOUND,
                        "Media file %d has no %s rendition".formatted(mediaFileId, variant),
                        ErrorParams.of("mediaFileId", mediaFileId, "variant", variant.name())));

        return new StreamedVariant(storageService.get(row.getStorageKey()), row.getContentType(),
                row.getSizeBytes(), file.getChecksumSha256());
    }

    // ---------------------------------------------------------------- authorization
    //
    // Resolved from the purpose at runtime rather than from a @PreAuthorize string, because the
    // required permission is request data — the annotation could only express a generic
    // MEDIA_MANAGE, which is exactly the permission D128 forbids: anyone holding it could attach a
    // file to any record in the system. Authorization is the owner's, never the media module's.

    private void requireManage(MediaPurpose purpose) {
        require(purpose.getManagePermission(), purpose);
    }

    private void requireView(MediaPurpose purpose) {
        require(purpose.getViewPermission(), purpose);
    }

    /**
     * A file is readable if <em>any</em> purpose it is linked under is readable.
     *
     * <p>Every shipping purpose is single-valued, so this set holds one element today. It takes a
     * set anyway because the alternative — picking "the" purpose — has to guess, and the guess is
     * wrong exactly when a file is shared across purposes with different permissions.
     */
    private void requireViewOfAny(Set<MediaPurpose> purposes) {
        boolean permitted = purposes.stream()
                .anyMatch(purpose -> securityService.hasPermission(purpose.getViewPermission()));
        if (!permitted) {
            throw new AuthorizationException(CommonErrorCode.ACCESS_DENIED,
                    "Caller holds none of the view permissions for purposes " + purposes,
                    ErrorParams.of("requiredAnyOf", purposes.stream()
                            .map(MediaPurpose::getViewPermission).distinct().toList()));
        }
    }

    private void require(String permission, MediaPurpose purpose) {
        if (!securityService.hasPermission(permission)) {
            throw new AuthorizationException(CommonErrorCode.ACCESS_DENIED,
                    "Caller lacks %s, required by media purpose %s".formatted(permission, purpose),
                    ErrorParams.of("requiredPermission", permission, "purpose", purpose.name()));
        }
    }

    // ---------------------------------------------------------------- upload helpers

    private void requireOwnerExists(Long tenantId, MediaOwnerType ownerType, Long ownerId) {
        // Called with the REQUEST's tenant, which is what closes the cross-tenant hole: an ownerId
        // belonging to another tenant is indistinguishable from one that does not exist, and both
        // answer 404.
        if (!resolverRegistry.require(ownerType).exists(tenantId, ownerId)) {
            throw new ResourceNotFoundException(MediaErrorCode.MEDIA_OWNER_NOT_FOUND,
                    "No %s %d for tenant %d".formatted(ownerType, ownerId, tenantId),
                    ErrorParams.of("ownerType", ownerType.name(), "ownerId", ownerId));
        }
    }

    private byte[] readBytes(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new ValidationException(MediaErrorCode.MEDIA_FILE_EMPTY, "Upload carried no bytes");
        }
        try {
            return upload.getBytes();
        } catch (IOException ex) {
            throw new ValidationException(MediaErrorCode.MEDIA_FILE_EMPTY,
                    "Upload could not be read: " + ex);
        }
    }

    /**
     * Settles what the file is from its bytes, not from what the request claimed.
     *
     * @return the detected content type, which is what gets stored and echoed back on every read
     */
    private String validateContentType(MediaPurpose purpose, byte[] bytes, MultipartFile upload) {
        String detected = contentTypeSniffer.sniff(bytes);
        if (ContentTypeSniffer.HEIC.equals(detected)) {
            // Named rather than lumped into "unsupported" so the UI can tell a phone user what to
            // do about it. Java has no HEIC decoder without a native plugin.
            throw new ValidationException(MediaErrorCode.MEDIA_HEIC_NOT_SUPPORTED,
                    "HEIC upload rejected: " + upload.getOriginalFilename(),
                    ErrorParams.of("filename", upload.getOriginalFilename()));
        }
        if (detected == null || !purpose.allows(detected)) {
            throw new ValidationException(MediaErrorCode.MEDIA_UNSUPPORTED_CONTENT_TYPE,
                    "Purpose %s does not allow %s (declared %s)"
                            .formatted(purpose, detected, upload.getContentType()),
                    ErrorParams.of(
                            "purpose", purpose.name(),
                            "detectedContentType", detected,
                            "allowedContentTypes", List.copyOf(purpose.getAllowedContentTypes())));
        }
        return detected;
    }

    private void validateSize(MediaPurpose purpose, int sizeBytes) {
        if (sizeBytes > purpose.getMaxSizeBytes()) {
            throw new ValidationException(MediaErrorCode.MEDIA_FILE_TOO_LARGE,
                    "Upload of %d bytes exceeds the %s ceiling of %d"
                            .formatted(sizeBytes, purpose, purpose.getMaxSizeBytes()),
                    ErrorParams.of(
                            "purpose", purpose.name(),
                            "sizeBytes", sizeBytes,
                            "maxSizeBytes", purpose.getMaxSizeBytes()));
        }
    }

    private MediaFile newMediaFile(Long tenantId, Long userId, MultipartFile upload,
                                   String contentType, byte[] original,
                                   ImageTranscoder.Transcoded transcoded) {
        MediaFile file = new MediaFile();
        file.setTenantId(tenantId);
        file.setOriginalFilename(trimFilename(upload.getOriginalFilename()));
        file.setContentType(contentType);
        file.setSizeBytes((long) original.length);
        file.setWidth(transcoded.width());
        file.setHeight(transcoded.height());
        file.setChecksumSha256(sha256(original));
        file.setCreatedBy(userId);
        file.setUpdatedBy(userId);
        return file;
    }

    /**
     * Writes every rendition's bytes and returns the rows that will describe them.
     *
     * <p>The rows are built but not saved — that is {@link MediaCommitService}'s transaction. If
     * this method throws halfway, the objects already written are orphans with no row, which is the
     * failure mode this ordering chooses deliberately.
     */
    private List<MediaVariantRow> storeAllBytes(Long tenantId, MediaPurpose purpose, String uuid,
                                                String contentType, byte[] original,
                                                ImageTranscoder.Transcoded transcoded) {
        List<MediaVariantRow> variants = new ArrayList<>();
        variants.add(storeOne(tenantId, purpose, uuid, MediaVariantType.ORIGINAL, original,
                contentType, transcoded.width(), transcoded.height()));
        for (ImageTranscoder.DerivativeRendition derivative : transcoded.derivatives()) {
            DerivativeSet.Rendition rendition = derivative.rendition();
            ImageTranscoder.Rendered rendered = derivative.rendered();
            variants.add(storeOne(tenantId, purpose, uuid, rendition.variant(), rendered.bytes(),
                    rendered.contentType(), rendered.width(), rendered.height()));
        }
        return variants;
    }

    private MediaVariantRow storeOne(Long tenantId, MediaPurpose purpose, String uuid,
                                     MediaVariantType variant, byte[] bytes, String contentType,
                                     int width, int height) {
        String key = StorageKeys.build(tenantId, purpose.getOwnerType(), uuid, variant, contentType);
        storageService.put(key, bytes, contentType);

        MediaVariantRow row = new MediaVariantRow();
        row.setVariant(variant);
        row.setStorageKey(key);
        row.setContentType(contentType);
        row.setWidth(width);
        row.setHeight(height);
        row.setSizeBytes((long) bytes.length);
        return row;
    }

    private MediaLink newLink(Long tenantId, Long userId, MediaPurpose purpose, Long ownerId) {
        MediaLink link = new MediaLink();
        link.setTenantId(tenantId);
        link.setOwnerType(purpose.getOwnerType());
        link.setOwnerId(ownerId);
        link.setPurpose(purpose);
        link.setSortOrder(0);
        link.setCreatedBy(userId);
        link.setUpdatedBy(userId);
        return link;
    }

    // ---------------------------------------------------------------- read helpers

    private List<MediaResponse> toResponses(List<MediaLink> links) {
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> fileIds = links.stream().map(MediaLink::getMediaFileId).distinct().toList();
        Map<Long, MediaFile> files = mediaFileRepository.findAllById(fileIds).stream()
                .collect(Collectors.toMap(MediaFile::getId, Function.identity()));
        Map<Long, List<MediaVariantRow>> variants =
                mediaVariantRepository.findByMediaFileIdIn(fileIds).stream()
                        .collect(Collectors.groupingBy(MediaVariantRow::getMediaFileId));

        return links.stream()
                .map(link -> mediaMapper.toResponse(link, files.get(link.getMediaFileId()),
                        variants.getOrDefault(link.getMediaFileId(), List.of())))
                .toList();
    }

    private String trimFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        return filename.length() <= 255 ? filename : filename.substring(filename.length() - 255);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by every JRE", ex);
        }
    }
}
