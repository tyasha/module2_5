package org.example.filestorage.repository;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.model.File;
import org.example.filestorage.model.FileStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class FileRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private FileRepository fileRepository;

    @Test
    void savesAndFindsFileById() {
        File file = new File(null, "report.pdf", "bucket/report.pdf", FileStatus.ACTIVE);

        File saved = fileRepository.save(file).block();

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();

        StepVerifier.create(fileRepository.findById(saved.getId()))
                .expectNextMatches(found ->
                        found.getName().equals("report.pdf") && found.getStatus() == FileStatus.ACTIVE)
                .verifyComplete();
    }
}
