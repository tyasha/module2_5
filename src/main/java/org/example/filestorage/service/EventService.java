package org.example.filestorage.service;

import org.example.filestorage.dto.EventDto;
import org.example.filestorage.model.EventStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventService {

    Mono<EventDto> getById(Integer id);

    Flux<EventDto> getAll();

    Mono<EventDto> delete(Integer id);

    Mono<Void> create(Integer userId, Integer fileId, EventStatus status);
}
