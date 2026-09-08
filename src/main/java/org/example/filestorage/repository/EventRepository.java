package org.example.filestorage.repository;

import org.example.filestorage.model.Event;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface EventRepository extends ReactiveCrudRepository<Event, Integer> {

    @Query("SELECT user_id FROM events WHERE file_id = :fileId AND status = 'CREATED'")
    Mono<Integer> findOwnerUserIdByFileId(Integer fileId);
}
