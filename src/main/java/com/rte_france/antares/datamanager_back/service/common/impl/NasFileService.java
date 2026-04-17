package com.rte_france.antares.datamanager_back.service.common.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import lombok.Getter;
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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NasFileService {

    private final TimeSeriesReader reader;

    @Getter
    private final TimeSeriesWriter writer;


    private final AntaresDataManagerProperties antaresDataManagerProperties;

    /**
     * Loads a file as a resource.
     *
     * @param filename the name of the file to load
     * @return the loaded file as a Resource
     * @throws FileNotFoundException if the file is not found or is not readable
     */
    public Resource loadFile(String filename) throws FileNotFoundException {
        Path filePath = Path.of(antaresDataManagerProperties.getNasDirectory()).resolve(filename);
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
    public void saveFile(String filename, byte[] content, String outputDir) throws IOException {
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(content, "content must not be null");

        if (filename.contains("..") || filename.isBlank()) {
            throw TechnicalException.builder()
                    .message("Invalid file name: " + filename)
                    .build();
        }

        String nasDir = antaresDataManagerProperties.getNasDirectory();

        Path nasPath = Path.of(nasDir).toAbsolutePath().normalize();

        Path relativeOutputDir = Path.of(outputDir);
        if (relativeOutputDir.isAbsolute()) {
            throw TechnicalException.builder()
                    .message("Output directory must be a relative path: " + outputDir)
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

    /**
     * Saves a time series matrix read from the given path to NAS with a unique filename.
     *
     * @param inputPath Path to input .txt or .xlsx file
     * @param outputDir Output directory relative to NAS root
     * @param sheetName Optional sheet name (for Excel files)
     * @return Saved filename
     * @throws IOException on read/write failure
     */
    public String saveMatrixToNas(Path inputPath, String outputDir, String sheetName) throws IOException {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        var name = inputPath.getFileName().toString().toLowerCase();
        TimeSeriesMatrix matrix;
        try {
            if (name.endsWith(".txt") || name.endsWith(".csv")) {
                matrix = reader.readFromTxt(inputPath);
            } else if (name.endsWith(".xlsx")) {
                matrix = reader.readFromXlsx(inputPath, sheetName);
            } else {
                throw TechnicalException.builder().message("Unsupported input format: " + name).build();
            }
        } catch (Exception e) {
            throw TechnicalException.builder()
                    .message("Failed to read time series matrix from file: " + inputPath.getFileName())
                    .cause(e)
                    .build();

        }
        var outputFileName = generateUniqueFileName(inputPath.getFileName().toString());
        saveMatrix(outputFileName, matrix, outputDir);
        setFilePermissions(inputPath);
        return outputFileName;
    }

    public String saveMatrixToNas(Path inputPath, String outputDir) throws IOException {
        return saveMatrixToNas(inputPath, outputDir, null);
    }

    /**
     * Reads a time series matrix from the given path without saving it.
     *
     * @param inputPath Path to input .txt, .csv, or .xlsx file
     * @param sheetName Optional sheet name / horizon (for Excel files)
     * @return The parsed TimeSeriesMatrix
     */
    public TimeSeriesMatrix readMatrix(Path inputPath, String sheetName) {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        var name = inputPath.getFileName().toString().toLowerCase();
        try {
            if (name.endsWith(".txt") || name.endsWith(".csv")) {
                return reader.readFromTxt(inputPath);
            } else if (name.endsWith(".xlsx")) {
                return reader.readFromXlsx(inputPath, sheetName);
            } else {
                throw TechnicalException.builder().message("Unsupported input format: " + name).build();
            }
        } catch (TechnicalException e) {
            throw e;
        } catch (Exception e) {
            throw TechnicalException.builder()
                    .message("Failed to read time series matrix from file: " + inputPath.getFileName())
                    .cause(e)
                    .build();
        }
    }

    public String saveMatrixToNas(TimeSeriesMatrix matrix, String baseName, String outputDir) throws IOException {
        Objects.requireNonNull(matrix, "matrix must not be null");
        Objects.requireNonNull(baseName, "baseName must not be null");
        var outputFileName = generateUniqueFileName(baseName);
        saveMatrix(outputFileName, matrix, outputDir);
        return outputFileName;
    }

    /**
     * Saves pre-serialized Arrow bytes to NAS with a unique filename.
     * Use this when the same matrix is written multiple times to avoid re-serializing.
     *
     * @param data     pre-serialized bytes (from {@link com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter#writeToByteArray})
     * @param baseName base filename (extension will be replaced)
     * @param outputDir output directory relative to NAS root
     * @return the saved filename
     */
    public String saveMatrixBytesToNas(byte[] data, String baseName, String outputDir) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(baseName, "baseName must not be null");
        var outputFileName = generateUniqueFileName(baseName);
        saveFile(outputFileName, data, outputDir);
        return outputFileName;
    }

    private String generateUniqueFileName(String baseName) {
        if (baseName.toLowerCase().endsWith("." + writer.getDefaultFileExtension())) {
            baseName = baseName.substring(0, baseName.length() - (writer.getDefaultFileExtension().length() + 1));
        }
        return baseName + "." + UUID.randomUUID() + "." + writer.getDefaultFileExtension();
    }

    private void saveMatrix(String fileName, TimeSeriesMatrix matrix, String outputDir) throws IOException {
        byte[] data = writer.writeToByteArray(matrix);
        saveFile(fileName, data, outputDir);
    }

    private void setFilePermissions(Path path) throws IOException {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }

}