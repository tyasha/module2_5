package org.example.filestorage.controller;

import jakarta.validation.Valid;
import org.example.filestorage.dto.RenameRequest;
import org.example.filestorage.dto.UserDto;
import org.example.filestorage.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public Mono<UserDto> getById(@PathVariable Integer id) {
        return userService.getById(id);
    }

    @GetMapping
    public Flux<UserDto> getAll() {
        return userService.getAll();
    }

    @PutMapping("/{id}")
    public Mono<UserDto> rename(@PathVariable Integer id, @Valid @RequestBody RenameRequest request) {
        return userService.rename(id, request.username());
    }

    @DeleteMapping("/{id}")
    public Mono<UserDto> delete(@PathVariable Integer id) {
        return userService.delete(id);
    }
}
