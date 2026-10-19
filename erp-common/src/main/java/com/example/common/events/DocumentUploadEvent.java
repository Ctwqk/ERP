package com.example.common.events;

import java.util.List;
import java.util.UUID;

/**
 * Published by item-service once an uploaded document has been verified in object storage.
 *
 * @param documentIds      ids of the uploaded documents
 * @param documentType     {@code DocType} name, e.g. {@code ORDER} or {@code ITEM}
 * @param linkId           id of the item/order the document is attached to
 * @param urls             optional download URLs (currently unused)
 * @param uploadedByUserId the uploader's user id (may be {@code null} for service callers)
 * @param uploadedBy       the uploader's subject (e-mail)
 */
public record DocumentUploadEvent(
        List<UUID> documentIds,
        String documentType,
        UUID linkId,
        List<String> urls,
        UUID uploadedByUserId,
        String uploadedBy) {
}
