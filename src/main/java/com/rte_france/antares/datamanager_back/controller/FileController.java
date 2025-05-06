package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.service.impl.NasFileService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/files")
public class FileController {

    private final NasFileService nasFileService;
    private final AntaressDataManagerProperties properties;

    @Operation(summary = "Retrieves a file as a downloadable resource")
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getFile(@PathVariable String filename) {
        try {
            Resource resource = nasFileService.loadFile(filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @Operation(summary = "Uploads a file to the server")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        Objects.requireNonNull(file);
        var fileName = file.getOriginalFilename();
        if (fileName == null || fileName.contains("..")) {
            return ResponseEntity.badRequest().body("Invalid file name: " + fileName);
        }
        var targetPath = Path.of(properties.getNasDirectory()).resolve(fileName).normalize();
        if (!targetPath.startsWith(properties.getNasDirectory())) {
            throw TechnicalException.builder().message("Path outside of target: " + targetPath).build();
        }

        nasFileService.saveFile(targetPath.toString(), file.getBytes());
        return ResponseEntity.ok("Fichier uploadé avec succès !");
    }
}
