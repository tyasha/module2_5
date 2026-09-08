package org.example.filestorage.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.filestorage.dto.FileDto;
import org.example.filestorage.exception.NotFoundException;
import org.example.filestorage.mapper.FileMapper;
import org.example.filestorage.model.EventStatus;
import org.example.filestorage.model.File;
import org.example.filestorage.model.FileStatus;
import org.example.filestorage.repository.FileRepository;
import org.example.filestorage.service.EventService;
import org.example.filestorage.service.FileContentService;
import org.example.filestorage.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private final FileRepository fileRepository;
    private final EventService eventService;
    private final FileContentService fileContentService;
    private final FileMapper fileMapper;

    public FileServiceImpl(FileRepository fileRepository,
                            EventService eventService,
                            FileContentService fileContentService,
                            FileMapper fileMapper) {
        this.fileRepository = fileRepository;
        this.eventService = eventService;
        this.fileContentService = fileContentService;
        this.fileMapper = fileMapper;
    }

    @Override
    public Mono<FileDto> getById(Integer id) {
        return fileRepository.findByIdAndStatus(id, FileStatus.ACTIVE)
                .switchIfEmpty(Mono.error(new NotFoundException("File", id)))
                .map(fileMapper::toDto)
                .doOnNext(dto -> log.debug("Найден файл id={}", id));
    }

    @Override
    public Flux<FileDto> getAll() {
        return fileRepository.findAllByStatus(FileStatus.ACTIVE)
                .map(fileMapper::toDto)
                .doOnComplete(() -> log.debug("Отдан список файлов"));
    }

    @Override
    @Transactional
    public Mono<FileDto> upload(String name, byte[] content, Integer userId) {
        String key = UUID.randomUUID().toString();

        Mono<File> persistFileAndEvent = fileRepository.save(new File(null, name, key, FileStatus.ACTIVE))
                .flatMap(savedFile -> eventService.create(userId, savedFile.getId(), EventStatus.CREATED)
                        .thenReturn(savedFile))
                .onErrorResume(e -> fileContentService.delete(key).then(Mono.error(e)));

        return fileContentService.put(key, content)
                .then(persistFileAndEvent)
                .map(fileMapper::toDto)
                .doOnSuccess(dto -> log.info("Файл '{}' загружен, id={}, userId={}", dto.name(), dto.id(), userId))
                .doOnError(e -> log.error("Не удалось загрузить файл '{}', userId={}", name, userId, e));
    }

    @Override
    public Flux<byte[]> getContent(Integer id) {
        return fileRepository.findByIdAndStatus(id, FileStatus.ACTIVE)
                .switchIfEmpty(Mono.error(new NotFoundException("File", id)))
                .doOnNext(file -> log.debug("Отдаётся содержимое файла id={}", id))
                .flatMapMany(file -> fileContentService.get(file.getLocation()));
    }

    @Override
    @Transactional
    public Mono<FileDto> delete(Integer id, Integer userId) {
        return fileRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("File", id)))
                .flatMap(file -> {
                    file.setStatus(FileStatus.ARCHIVED);
                    return fileRepository.save(file);
                })
                .flatMap(savedFile -> eventService.create(userId, savedFile.getId(), EventStatus.DELETED)
                        .thenReturn(savedFile))
                .map(fileMapper::toDto)
                .doOnSuccess(dto -> log.info("Файл id={} архивирован, userId={}", id, userId))
                .doOnError(e -> !(e instanceof NotFoundException),
                        e -> log.error("Не удалось удалить файл id={}, userId={}", id, userId, e));
    }
}
