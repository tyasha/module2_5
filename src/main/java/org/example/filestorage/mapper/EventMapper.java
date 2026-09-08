package org.example.filestorage.mapper;

import org.example.filestorage.dto.EventDto;
import org.example.filestorage.model.Event;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EventMapper {

    EventDto toDto(Event event);
}
