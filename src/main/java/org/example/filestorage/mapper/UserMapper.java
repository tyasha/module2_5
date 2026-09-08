package org.example.filestorage.mapper;

import org.example.filestorage.dto.UserDto;
import org.example.filestorage.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);
}
