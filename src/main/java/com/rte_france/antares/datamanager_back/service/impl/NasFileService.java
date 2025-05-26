package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

@Slf4j
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
     * @param content  the content to save in the file
     * @throws IOException if an I/O error occurs or if the file name is invalid
     */
    public void saveFile(String filename, byte[] content) throws IOException {
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(content, "content must not be null");

        if (filename.contains("..") || filename.isBlank()) {
            throw TechnicalException.builder()
                    .message("Invalid file name: " + filename)
                    .build();
        }

        String nasDir = antaressDataManagerProperties.getNasDirectory();
        String outputLoadDir = antaressDataManagerProperties.getOutputLoadDirectory();

        Path nasPath = Path.of(nasDir).toAbsolutePath().normalize();

        Path relativeOutputDir = Path.of(outputLoadDir);
        if (relativeOutputDir.isAbsolute()) {
            throw TechnicalException.builder()
                    .message("Output directory must be a relative path: " + outputLoadDir)
                    .build();
        }

        Path targetDirectory = nasPath.resolve(relativeOutputDir).normalize();
        Path filePath = targetDirectory.resolve(filename).normalize();

        // Vérification que le fichier reste bien dans le répertoire NAS
        if (!filePath.startsWith(nasPath)) {
            throw TechnicalException.builder()
                    .message("Path outside of the NAS directory: " + filePath)
                    .build();
        }

        // Création du dossier cible si nécessaire
        Files.createDirectories(targetDirectory);

        // Écriture du fichier
        Files.write(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

}