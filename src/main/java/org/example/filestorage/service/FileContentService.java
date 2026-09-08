package org.example.filestorage.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileContentService {

    Mono<Void> put(String key, byte[] content);

    Flux<byte[]> get(String key);

    Mono<Void> delete(String key);
}
