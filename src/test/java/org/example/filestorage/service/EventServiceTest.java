package org.example.filestorage.service;

import org.example.filestorage.exception.NotFoundException;
import org.example.filestorage.mapper.EventMapper;
import org.example.filestorage.model.Event;
import org.example.filestorage.model.EventStatus;
import org.example.filestorage.repository.EventRepository;
import org.example.filestorage.service.impl.EventServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

class EventServiceTest {

    private EventRepository eventRepository;
    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        EventMapper eventMapper = Mappers.getMapper(EventMapper.class);
        eventService = new EventServiceImpl(eventRepository, eventMapper);
    }

    @Test
    void getByIdReturnsMappedDto() {
        Event event = new Event(1, 10, 20, EventStatus.CREATED, LocalDateTime.now());
        when(eventRepository.findById(1)).thenReturn(Mono.just(event));

        StepVerifier.create(eventService.getById(1))
                .expectNextMatches(dto -> dto.id().equals(1) && dto.userId().equals(10) && dto.fileId().equals(20))
                .verifyComplete();
    }

    @Test
    void getByIdThrowsNotFoundWhenEventDoesNotExist() {
        when(eventRepository.findById(1)).thenReturn(Mono.empty());

        StepVerifier.create(eventService.getById(1))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void deleteRemovesRowFromRepository() {
        Event event = new Event(1, 10, 20, EventStatus.CREATED, LocalDateTime.now());
        when(eventRepository.findById(1)).thenReturn(Mono.just(event));
        when(eventRepository.delete(event)).thenReturn(Mono.empty());

        StepVerifier.create(eventService.delete(1))
                .expectNextMatches(dto -> dto.id().equals(1) && dto.userId().equals(10) && dto.fileId().equals(20))
                .verifyComplete();

        verify(eventRepository).delete(event);
    }

    @Test
    void deleteThrowsNotFoundWhenEventDoesNotExist() {
        when(eventRepository.findById(1)).thenReturn(Mono.empty());

        StepVerifier.create(eventService.delete(1))
                .expectError(NotFoundException.class)
                .verify();

        verify(eventRepository, never()).delete(any(Event.class));
    }
}
