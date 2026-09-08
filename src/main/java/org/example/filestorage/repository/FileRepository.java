package org.example.filestorage.repository;

import org.example.filestorage.model.File;
import org.example.filestorage.model.FileStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileRepository extends ReactiveCrudRepository<File, Integer> {

    Mono<File> findByIdAndStatus(Integer id, FileStatus status);

    Flux<File> findAllByStatus(FileStatus status);
}
