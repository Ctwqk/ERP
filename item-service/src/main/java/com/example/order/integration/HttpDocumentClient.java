package com.example.order.integration;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

@Component
public class HttpDocumentClient implements DocumentClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String DOWNLOAD_URL_TEMPLATE = "http://document-service:8080/api/documents/documents/%s:download-url";

    @Override
    public InputStream download(UUID documentId) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> resp = restTemplate.getForObject(
                DOWNLOAD_URL_TEMPLATE.formatted(documentId),
                Map.class);
        if (resp == null || !resp.containsKey("url")) {
            throw new IllegalStateException("No download url returned");
        }
        return new URL(resp.get("url")).openStream();
    }
}


