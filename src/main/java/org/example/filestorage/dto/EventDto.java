package org.example.filestorage.dto;

import org.example.filestorage.model.EventStatus;

import java.time.LocalDateTime;

public record EventDto(Integer id, Integer userId, Integer fileId, EventStatus status, LocalDateTime timestamp) {
}
