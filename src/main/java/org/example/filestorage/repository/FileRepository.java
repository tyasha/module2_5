package org.example.filestorage.repository;

import org.example.filestorage.model.File;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface FileRepository extends ReactiveCrudRepository<File, Integer> {
}
