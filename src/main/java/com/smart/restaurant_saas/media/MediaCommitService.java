package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.media.enums.MediaPurpose;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every row write media performs, and nothing else.
 *
 * <p>Split from {@link MediaService} because the two halves of an upload have to sit on opposite
 * sides of a transaction boundary — bytes are written before this commits — and a
 * {@code @Transactional} method a bean calls on itself is not transactional at all. Making the
 * boundary a separate bean makes it a boundary the compiler helps keep.
 */
@Service
@RequiredArgsConstructor
public class MediaCommitService {

    private final MediaFileRepository mediaFileRepository;
    private final MediaVariantRepository mediaVariantRepository;
    private final MediaLinkRepository mediaLinkRepository;
    private final MediaDeletionQueueRepository deletionQueueRepository;
    private final TenantTimeZoneService tenantTimeZoneService;

    /** A file whose bytes are already stored, waiting for the rows that make it reachable. */
    public record PendingUpload(MediaFile file, List<MediaVariantRow> variants, MediaLink link) {}

    /**
     * Writes the file, its variants and the link, replacing any incumbent on a single-valued
     * purpose. Replacement and the old file's removal happen here, in one transaction: a partial
     * swap would leave either two links where the index allows one, or none where a product had an
     * image a moment ago.
     */
    @Transactional
    public MediaLink commitUpload(PendingUpload pending) {
        MediaLink link = pending.link();
        MediaPurpose purpose = link.getPurpose();
        if (purpose.isSingleValued()) {
            List<MediaLink> incumbents = mediaLinkRepository
                    .findByTenantIdAndOwnerTypeAndOwnerIdAndPurposeOrderBySortOrderAscIdAsc(
                            link.getTenantId(), link.getOwnerType(), link.getOwnerId(), purpose);
            incumbents.forEach(this::detachAndQueueBytes);
            // The partial unique index is checked at statement time, so the incumbent's DELETE has
            // to reach the database before the new INSERT. Without this the swap fails on the
            // index it exists to satisfy.
            mediaLinkRepository.flush();
        }

        MediaFile file = mediaFileRepository.save(pending.file());
        for (MediaVariantRow variant : pending.variants()) {
            variant.setMediaFileId(file.getId());
        }
        mediaVariantRepository.saveAll(pending.variants());

        link.setMediaFileId(file.getId());
        return mediaLinkRepository.save(link);
    }

    /**
     * Removes a link, and the file behind it when nothing else points at it.
     *
     * <p>Commit first, bytes second: the storage keys go to {@code media_deletion_queue} in this
     * same transaction and a job removes the objects afterwards. Deleting bytes inline would mean a
     * rollback destroys a file whose row came back.
     */
    @Transactional
    public void deleteLink(MediaLink link) {
        detachAndQueueBytes(link);
    }

    private void detachAndQueueBytes(MediaLink link) {
        Long mediaFileId = link.getMediaFileId();
        mediaLinkRepository.delete(link);
        mediaLinkRepository.flush();

        // Nothing creates a second link to one file today. The check is here because deletion is
        // permanent, so the cost of being wrong is asymmetric: a skipped delete leaks bytes the
        // orphan sweep reclaims, while a premature one breaks a live image with no way back.
        if (mediaLinkRepository.existsByMediaFileId(mediaFileId)) {
            return;
        }

        List<MediaVariantRow> variants = mediaVariantRepository.findByMediaFileIdOrderByIdAsc(mediaFileId);
        enqueueForByteDeletion(link.getTenantId(), variants);
        mediaVariantRepository.deleteAll(variants);
        mediaFileRepository.deleteById(mediaFileId);
    }

    private void enqueueForByteDeletion(Long tenantId, List<MediaVariantRow> variants) {
        LocalDateTime now = LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId));
        List<MediaDeletionQueueEntry> entries = new ArrayList<>(variants.size());
        for (MediaVariantRow variant : variants) {
            MediaDeletionQueueEntry entry = new MediaDeletionQueueEntry();
            entry.setTenantId(tenantId);
            entry.setStorageKey(variant.getStorageKey());
            entry.setEnqueuedAt(now);
            entries.add(entry);
        }
        deletionQueueRepository.saveAll(entries);
    }
}
