package org.example.filestorage.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.filestorage.dto.EventDto;
import org.example.filestorage.exception.NotFoundException;
import org.example.filestorage.mapper.EventMapper;
import org.example.filestorage.model.Event;
import org.example.filestorage.model.EventStatus;
import org.example.filestorage.repository.EventRepository;
import org.example.filestorage.service.EventService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public EventServiceImpl(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Override
    public Mono<EventDto> getById(Integer id) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Event", id)))
                .map(eventMapper::toDto)
                .doOnNext(dto -> log.debug("Найдено событие id={}", id));
    }

    @Override
    public Flux<EventDto> getAll() {
        return eventRepository.findAll()
                .map(eventMapper::toDto)
                .doOnComplete(() -> log.debug("Отдан список событий"));
    }

    @Override
    public Mono<EventDto> delete(Integer id) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Event", id)))
                .flatMap(event -> eventRepository.delete(event).thenReturn(event))
                .map(eventMapper::toDto)
                .doOnSuccess(dto -> log.info("Событие id={} удалено", id))
                .doOnError(e -> !(e instanceof NotFoundException),
                        e -> log.error("Не удалось удалить событие id={}", id, e));
    }

    @Override
    public Mono<Void> create(Integer userId, Integer fileId, EventStatus status) {
        Event event = new Event(null, userId, fileId, status, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        return eventRepository.save(event)
                .doOnNext(saved -> log.debug("Создано событие {} для file={}, user={}", status, fileId, userId))
                .then();
    }
}
