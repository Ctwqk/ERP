package com.example.document.config;

import com.example.document.service.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Best-effort bucket creation at start-up; uploads also ensure the bucket lazily, so MinIO may start later. */
@Component
public class BucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);

    private final DocumentStorage storage;

    public BucketInitializer(DocumentStorage storage) {
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            storage.ensureBucket();
        } catch (RuntimeException e) {
            log.warn("Could not verify bucket '{}' at start-up ({}); will retry on first upload", storage.bucket(),
                    e.getMessage());
        }
    }
}
