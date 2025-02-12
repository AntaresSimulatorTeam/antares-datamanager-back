package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
@Service
@RequiredArgsConstructor
public class NasFileService {

    private final AntaressDataManagerProperties antaressDataManagerProperties;

    public Resource loadFile(String filename) throws MalformedURLException {
        Path filePath = Paths.get(antaressDataManagerProperties.getNasDirectory()).resolve(filename);
        Resource resource = new UrlResource(filePath.toUri());

        if (resource.exists() || resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("Fichier non trouvé ou illisible : " + filename);
        }
    }

    public void saveFile(String filename, byte[] content) throws IOException {
        Path filePath = Paths.get(antaressDataManagerProperties.getNasDirectory()).resolve(filename);
        Files.write(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}