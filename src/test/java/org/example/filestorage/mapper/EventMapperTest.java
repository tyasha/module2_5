package org.example.filestorage.mapper;

import org.example.filestorage.dto.EventDto;
import org.example.filestorage.model.Event;
import org.example.filestorage.model.EventStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EventMapperTest {

    private final EventMapper mapper = Mappers.getMapper(EventMapper.class);

    @Test
    void mapsEventToDto() {
        LocalDateTime now = LocalDateTime.now();
        Event event = new Event(1, 10, 20, EventStatus.CREATED, now);

        EventDto dto = mapper.toDto(event);

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.userId()).isEqualTo(10);
        assertThat(dto.fileId()).isEqualTo(20);
        assertThat(dto.status()).isEqualTo(EventStatus.CREATED);
        assertThat(dto.timestamp()).isEqualTo(now);
    }
}
