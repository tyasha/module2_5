package org.example.filestorage.controller;

import org.example.filestorage.dto.FileDto;
import org.example.filestorage.security.AuthenticatedUser;
import org.example.filestorage.service.FileService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<FileDto> upload(@RequestPart("file") FilePart filePart,
                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        return DataBufferUtils.join(filePart.content())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .flatMap(bytes -> fileService.upload(filePart.filename(), bytes, principal.userId()));
    }

    @GetMapping("/{id}")
    public Mono<FileDto> getById(@PathVariable Integer id) {
        return fileService.getById(id);
    }

    @GetMapping("/{id}/download")
    public Mono<ResponseEntity<Flux<byte[]>>> download(@PathVariable Integer id) {
        return fileService.getById(id)
                .map(fileDto -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileDto.name() + "\"")
                        .body(fileService.getContent(id)));
    }

    @GetMapping
    public Flux<FileDto> getAll() {
        return fileService.getAll();
    }

    @DeleteMapping("/{id}")
    public Mono<FileDto> delete(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return fileService.delete(id, principal.userId());
    }
}
