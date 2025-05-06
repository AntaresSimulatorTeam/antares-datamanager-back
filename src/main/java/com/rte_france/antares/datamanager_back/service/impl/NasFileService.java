package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
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
            throw TechnicalException.builder().message("Fichier non trouvé ou illisible : " + filename).build();
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
            throw TechnicalException.builder().message("Invalid file name: " + filename).build();
        }
        String nasDir = antaressDataManagerProperties.getNasDirectory();
        String trajFilePath = antaressDataManagerProperties.getTrajectoryFilePath();
        String loadDir = antaressDataManagerProperties.getLoadDirectory();
        String outputLoadDir = antaressDataManagerProperties.getOutputLoadDirectory();
        var targetDirectory = Path.of(nasDir).resolve(trajFilePath).resolve(loadDir).resolve(outputLoadDir)
                .toAbsolutePath()
                .normalize();
        var filePath = targetDirectory.resolve(filename).normalize();
        if (!filePath.startsWith(targetDirectory)) {
            throw TechnicalException.builder().message("Path outside of the NAS directory: " + filePath).build();
        }

        Files.write(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}