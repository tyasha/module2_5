package org.example.filestorage.mapper;

import org.example.filestorage.dto.FileDto;
import org.example.filestorage.model.File;
import org.mapstruct.Mapper;

// location осознанно отсутствует в FileDto — не поле для маппинга, а не забытое
@Mapper(componentModel = "spring")
public interface FileMapper {

    FileDto toDto(File file);
}
