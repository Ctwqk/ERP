package com.example.document.dto;

import com.example.document.domain.Document.DocType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * @param filename    original file name, also used as the document title
 * @param contentType MIME type stored with the document
 * @param sizeBytes   exact size of the file that will be uploaded (verified on completion)
 * @param purpose     {@code PRIMARY} (default) or {@code REFERENCE}
 * @param linkType    what the link id in the path refers to: {@code ITEM} (default) or {@code ORDER}
 * @param docType     document category; defaults to the link type
 */
public record InitUploadRequest(
        @NotBlank String filename,
        String contentType,
        @NotNull @Positive Long sizeBytes,
        String purpose,
        String linkType,
        DocType docType) {
}
