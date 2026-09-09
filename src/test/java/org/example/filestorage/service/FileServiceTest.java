package org.example.filestorage.service;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.config.MinioProperties;
import org.example.filestorage.dto.FileDto;
import org.example.filestorage.exception.FileStorageUnavailableException;
import org.example.filestorage.exception.NotFoundException;
import org.example.filestorage.mapper.FileMapper;
import org.example.filestorage.model.EventStatus;
import org.example.filestorage.model.FileStatus;
import org.example.filestorage.model.User;
import org.example.filestorage.model.UserRole;
import org.example.filestorage.model.UserStatus;
import org.example.filestorage.repository.EventRepository;
import org.example.filestorage.repository.FileRepository;
import org.example.filestorage.repository.UserRepository;
import org.example.filestorage.service.impl.FileContentServiceImpl;
import org.example.filestorage.service.impl.FileServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

class FileServiceTest extends AbstractIntegrationTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private FileMapper fileMapper;

    @Test
    void uploadStoresContentInMinioAndCreatesFilePlusEvent() {
        User user = userRepository.save(new User(null, "ivan", "password123", UserRole.USER, UserStatus.ACTIVE)).block();
        byte[] content = "hello world".getBytes();

        FileDto dto = fileService.upload("report.pdf", content, user.getId()).block();

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isNotNull();
        assertThat(dto.name()).isEqualTo("report.pdf");
        assertThat(dto.status()).isEqualTo(FileStatus.ACTIVE);

        StepVerifier.create(fileService.getContent(dto.id()).collectList())
                .expectNextMatches(chunks -> new String(joinChunks(chunks)).equals("hello world"))
                .verifyComplete();

        StepVerifier.create(eventRepository.findOwnerUserIdByFileId(dto.id()))
                .expectNext(user.getId())
                .verifyComplete();
    }

    private static byte[] joinChunks(List<byte[]> chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        chunks.forEach(out::writeBytes);
        return out.toByteArray();
    }

    @Test
    void deleteArchivesFileWithoutRemovingRow() {
        User user = userRepository.save(new User(null, "petr", "password123", UserRole.USER, UserStatus.ACTIVE)).block();
        FileDto uploaded = fileService.upload("doc.pdf", "content".getBytes(), user.getId()).block();

        FileDto deleted = fileService.delete(uploaded.id(), user.getId()).block();

        assertThat(deleted.status()).isEqualTo(FileStatus.ARCHIVED);
        StepVerifier.create(fileRepository.findById(uploaded.id()))
                .expectNextMatches(f -> f.getStatus() == FileStatus.ARCHIVED)
                .verifyComplete();

        StepVerifier.create(eventRepository.findAll()
                        .filter(e -> e.getFileId().equals(uploaded.id()) && e.getStatus() == EventStatus.DELETED))
                .expectNextMatches(e -> e.getUserId().equals(user.getId()))
                .verifyComplete();
    }

    @Test
    void archivedFileIsHiddenFromGetByIdGetAllAndGetContent() {
        User user = userRepository.save(new User(null, "sidor", "password123", UserRole.USER, UserStatus.ACTIVE)).block();
        FileDto uploaded = fileService.upload("secret.pdf", "content".getBytes(), user.getId()).block();

        fileService.delete(uploaded.id(), user.getId()).block();

        StepVerifier.create(fileService.getById(uploaded.id()))
                .expectError(NotFoundException.class)
                .verify();

        StepVerifier.create(fileService.getAll())
                .verifyComplete();

        StepVerifier.create(fileService.getContent(uploaded.id()))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void getByIdThrowsNotFoundForNonExistentId() {
        StepVerifier.create(fileService.getById(999_999))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void getContentStreamsLargeFileInMultipleChunks() {
        User user = userRepository.save(new User(null, "big-file-user", "password123", UserRole.USER, UserStatus.ACTIVE)).block();
        byte[] content = new byte[5 * 1024 * 1024]; // заведомо больше одного internal-чанка стрима
        ThreadLocalRandom.current().nextBytes(content);

        FileDto dto = fileService.upload("big.bin", content, user.getId()).block();

        StepVerifier.create(fileService.getContent(dto.id()).collectList())
                .assertNext(chunks -> {
                    assertThat(chunks.size()).isGreaterThan(1);
                    assertThat(joinChunks(chunks)).isEqualTo(content);
                })
                .verifyComplete();
    }

    @Test
    void uploadDoesNotCreateFileRowWhenPutFails() {
        MinioProperties badBucketProperties = new MinioProperties(
                "unused", "unused", "unused", "nonexistent-bucket-xyz");
        FileContentService brokenContentService = new FileContentServiceImpl(s3AsyncClient, badBucketProperties);
        FileService brokenFileService = new FileServiceImpl(fileRepository, eventService, brokenContentService, fileMapper);

        User user = userRepository.save(new User(null, "broken-put-user", "password123", UserRole.USER, UserStatus.ACTIVE)).block();

        StepVerifier.create(brokenFileService.upload("doc.pdf", "content".getBytes(), user.getId()))
                .expectError(FileStorageUnavailableException.class)
                .verify();

        StepVerifier.create(fileRepository.findAll())
                .verifyComplete();
    }

    @Test
    void uploadRollsBackFileWhenEventCreationFails() {
        int objectCountBefore = objectCountInBucket();

        StepVerifier.create(fileService.upload("doc.pdf", "content".getBytes(), 999_999))
                .expectError(DataIntegrityViolationException.class)
                .verify();

        StepVerifier.create(fileRepository.findAll())
                .verifyComplete();

        assertThat(objectCountInBucket()).isEqualTo(objectCountBefore);
    }

    private int objectCountInBucket() {
        return s3AsyncClient.listObjectsV2(r -> r.bucket(TEST_BUCKET))
                .join()
                .contents()
                .size();
    }
}
