package com.example.order.integration;

import java.io.InputStream;
import java.util.UUID;

public interface DocumentClient {

    InputStream download(UUID documentId) throws Exception;
}
