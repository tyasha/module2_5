package org.example.filestorage.service;

import org.example.filestorage.dto.FileDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileService {

    Mono<FileDto> getById(Integer id);

    Flux<FileDto> getAll();

    Mono<FileDto> upload(String name, byte[] content, Integer userId);

    Flux<byte[]> getContent(Integer id);

    Mono<FileDto> delete(Integer id, Integer userId);
}
