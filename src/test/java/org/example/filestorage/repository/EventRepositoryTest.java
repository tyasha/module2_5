package org.example.filestorage.repository;

import org.example.filestorage.AbstractIntegrationTest;
import org.example.filestorage.model.Event;
import org.example.filestorage.model.EventStatus;
import org.example.filestorage.model.File;
import org.example.filestorage.model.FileStatus;
import org.example.filestorage.model.User;
import org.example.filestorage.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EventRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void savesEventLinkedToRealUserAndFile() {
        User user = userRepository.save(new User(null, "ivan", UserStatus.ACTIVE)).block();
        File file = fileRepository.save(new File(null, "report.pdf", "bucket/report.pdf", FileStatus.ACTIVE)).block();

        // MySQL TIMESTAMP has no sub-second precision by default, so truncate the fixture
        // to seconds up front — otherwise the round-trip comparison below would never match.
        LocalDateTime timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        Event event = new Event(null, user.getId(), file.getId(), EventStatus.CREATED, timestamp);

        Event saved = eventRepository.save(event).block();

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(EventStatus.CREATED);

        StepVerifier.create(eventRepository.findById(saved.getId()))
                .expectNextMatches(found ->
                        found.getStatus() == EventStatus.CREATED && found.getTimestamp().equals(timestamp))
                .verifyComplete();
    }

    @Test
    void rejectsEventWithNonExistentUser() {
        Event event = new Event(null, 999_999, null, EventStatus.CREATED, LocalDateTime.now());

        StepVerifier.create(eventRepository.save(event))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void rejectsEventWithNonExistentFile() {
        User user = userRepository.save(new User(null, "ivan", UserStatus.ACTIVE)).block();

        Event event = new Event(null, user.getId(), 999_999, EventStatus.CREATED, LocalDateTime.now());

        StepVerifier.create(eventRepository.save(event))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void findsOwnerByCreatedEvent() {
        User owner = userRepository.save(new User(null, "petr", UserStatus.ACTIVE)).block();
        User otherUser = userRepository.save(new User(null, "sidor", UserStatus.ACTIVE)).block();
        File file = fileRepository.save(new File(null, "doc.pdf", "bucket/doc.pdf", FileStatus.ACTIVE)).block();

        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(5);
        // Earlier event, different user, non-CREATED status — rules out "earliest by timestamp".
        eventRepository.save(new Event(null, otherUser.getId(), file.getId(), EventStatus.UPDATED, createdAt.minusMinutes(1))).block();
        eventRepository.save(new Event(null, owner.getId(), file.getId(), EventStatus.CREATED, createdAt)).block();
        // Later event, different user, non-CREATED status — rules out "latest by timestamp".
        // Together with the earlier event above, the CREATED event is bracketed on both sides,
        // so only filtering on status = 'CREATED' (not "earliest" or "latest by timestamp") can pass.
        eventRepository.save(new Event(null, otherUser.getId(), file.getId(), EventStatus.DELETED, createdAt.plusMinutes(1))).block();

        StepVerifier.create(eventRepository.findOwnerUserIdByFileId(file.getId()))
                .expectNext(owner.getId())
                .verifyComplete();
    }
}
