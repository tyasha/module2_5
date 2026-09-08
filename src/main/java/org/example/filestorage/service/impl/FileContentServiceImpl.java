package org.example.filestorage.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.filestorage.config.MinioProperties;
import org.example.filestorage.exception.FileStorageUnavailableException;
import org.example.filestorage.service.FileContentService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.ByteBuffer;

@Slf4j
@Service
public class FileContentServiceImpl implements FileContentService {

    private final S3AsyncClient s3AsyncClient;
    private final MinioProperties minioProperties;

    public FileContentServiceImpl(S3AsyncClient s3AsyncClient, MinioProperties minioProperties) {
        this.s3AsyncClient = s3AsyncClient;
        this.minioProperties = minioProperties;
    }

    @Override
    public Mono<Void> put(String key, byte[] content) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(minioProperties.bucket())
                .key(key)
                .build();

        return Mono.fromFuture(() -> s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(content)))
                .then()
                .doOnError(e -> log.error("Не удалось сохранить файл в MinIO, key={}", key, e))
                .onErrorMap(e -> new FileStorageUnavailableException("Не удалось сохранить файл в MinIO: " + key, e));
    }

    @Override
    public Flux<byte[]> get(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(minioProperties.bucket())
                .key(key)
                .build();

        return Mono.fromFuture(() -> s3AsyncClient.getObject(request, AsyncResponseTransformer.toPublisher()))
                .flatMapMany(responsePublisher -> Flux.from(responsePublisher).map(FileContentServiceImpl::toByteArray))
                .doOnError(e -> log.error("Не удалось получить файл из MinIO, key={}", key, e))
                .onErrorMap(e -> new FileStorageUnavailableException("Не удалось получить файл из MinIO: " + key, e));
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    @Override
    public Mono<Void> delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(minioProperties.bucket())
                .key(key)
                .build();

        return Mono.fromFuture(() -> s3AsyncClient.deleteObject(request))
                .then()
                .doOnError(e -> log.error("Не удалось удалить файл из MinIO, key={}", key, e))
                .onErrorMap(e -> new FileStorageUnavailableException("Не удалось удалить файл из MinIO: " + key, e));
    }
}
