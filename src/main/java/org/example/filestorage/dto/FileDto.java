package org.example.filestorage.dto;

import org.example.filestorage.model.FileStatus;

// без location — см. дизайн-документ, раздел "Доступ к содержимому файла"
public record FileDto(Integer id, String name, FileStatus status) {
}
