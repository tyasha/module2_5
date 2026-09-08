package org.example.filestorage.mapper;

import org.example.filestorage.dto.UserDto;
import org.example.filestorage.model.User;
import org.example.filestorage.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void mapsUserToDto() {
        User user = new User(1, "ivan", UserStatus.ACTIVE);

        UserDto dto = mapper.toDto(user);

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.username()).isEqualTo("ivan");
        assertThat(dto.status()).isEqualTo(UserStatus.ACTIVE);
    }
}
