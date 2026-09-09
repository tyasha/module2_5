package org.example.filestorage.controller;

import org.example.filestorage.dto.EventDto;
import org.example.filestorage.service.EventService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/{id}")
    public Mono<EventDto> getById(@PathVariable Integer id) {
        return eventService.getById(id);
    }

    @GetMapping
    public Flux<EventDto> getAll() {
        return eventService.getAll();
    }

    @DeleteMapping("/{id}")
    public Mono<EventDto> delete(@PathVariable Integer id) {
        return eventService.delete(id);
    }
}
