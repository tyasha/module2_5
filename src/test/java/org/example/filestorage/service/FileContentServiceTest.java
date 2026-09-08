package org.example.filestorage.service;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.config.MinioProperties;
import org.example.filestorage.exception.FileStorageUnavailableException;
import org.example.filestorage.service.impl.FileContentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.util.List;

class FileContentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private FileContentService fileContentService;

    @Test
    void putsAndGetsContentByKey() {
        String key = "test-key-put-get";
        byte[] content = "hello minio".getBytes();

        fileContentService.put(key, content).block();

        StepVerifier.create(fileContentService.get(key).collectList())
                .expectNextMatches(chunks -> new String(joinChunks(chunks)).equals("hello minio"))
                .verifyComplete();
    }

    private static byte[] joinChunks(List<byte[]> chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        chunks.forEach(out::writeBytes);
        return out.toByteArray();
    }

    @Test
    void deletesContent() {
        String key = "test-key-delete";
        fileContentService.put(key, "to be deleted".getBytes()).block();

        fileContentService.delete(key).block();

        StepVerifier.create(fileContentService.get(key))
                .expectError(FileStorageUnavailableException.class)
                .verify();
    }

    @Test
    void putFailsFastWhenBucketDoesNotExist() {
        MinioProperties badBucketProperties = new MinioProperties(
                "unused", "unused", "unused", "nonexistent-bucket-xyz");
        FileContentService brokenService = new FileContentServiceImpl(s3AsyncClient, badBucketProperties);

        StepVerifier.create(brokenService.put("some-key", "content".getBytes()))
                .expectError(FileStorageUnavailableException.class)
                .verify();
    }
}
