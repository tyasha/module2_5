package org.example.filestorage.mapper;

import org.example.filestorage.dto.FileDto;
import org.example.filestorage.model.File;
import org.example.filestorage.model.FileStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class FileMapperTest {

    private final FileMapper mapper = Mappers.getMapper(FileMapper.class);

    @Test
    void mapsFileToDtoWithoutLocation() {
        File file = new File(1, "report.pdf", "bucket/report.pdf", FileStatus.ACTIVE);

        FileDto dto = mapper.toDto(file);

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.name()).isEqualTo("report.pdf");
        assertThat(dto.status()).isEqualTo(FileStatus.ACTIVE);
    }
}
