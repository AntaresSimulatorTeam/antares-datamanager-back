package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NasFileService {

    private final AntaressDataManagerProperties antaressDataManagerProperties;

    /**
     * Loads a file as a resource.
     *
     * @param filename the name of the file to load
     * @return the loaded file as a Resource
     * @throws FileNotFoundException if the file is not found or is not readable
     */
    public Resource loadFile(String filename) throws FileNotFoundException {
        Path filePath = Path.of(antaressDataManagerProperties.getNasDirectory()).resolve(filename);
        Resource resource = UrlResource.from(filePath.toUri());

        if (resource.exists() || resource.isReadable()) {
            return resource;
        } else {
            throw new FileNotFoundException("Fichier non trouvé ou illisible : " + filename);
        }
    }

    /**
     * Saves a file with the given content.
     *
     * @param filename the name of the file to save
     * @param content the content to save in the file
     * @throws IOException if an I/O error occurs or if the file name is invalid
     */
    public void saveFile(String filename, byte[] content) throws IOException {
        Objects.requireNonNull(filename);
        Objects.requireNonNull(content);
        if (filename.contains("..") || filename.isBlank()) {
            throw new IOException("Invalid file name: " + filename);
        }

        var targetDirectory = Path.of(antaressDataManagerProperties.getNasDirectory())
                .toAbsolutePath()
                .normalize();
        var filePath = targetDirectory.resolve(filename).normalize();
        if (!filePath.startsWith(targetDirectory)) {
            throw new IOException("Path outside of the NAS directory: " + filePath);
        }

        Files.write(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}