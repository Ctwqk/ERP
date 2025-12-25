package com.example.document.dto;

public record InitUploadRequest(String filename, String contentType, Long sizeBytes, String purpose, String linkType) {
}