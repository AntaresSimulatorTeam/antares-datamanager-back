package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.service.impl.NasFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.UrlResource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NasFileServiceTest {

  @InjectMocks
  private NasFileService nasFileService;

  @Mock
  private AntaressDataManagerProperties antaressDataManagerProperties;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void loadFile_fileExists() throws IOException {
    var filename = "testFile.txt";
    var filePath = Path.of("/nas").resolve(filename);
    when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/nas");

    var resource = mock(UrlResource.class);
    when(resource.exists()).thenReturn(true);
    when(resource.isReadable()).thenReturn(true);
    when(resource.getURI()).thenReturn(filePath.toUri());

    try (var mockedUrlResource = mockStatic(UrlResource.class)) {
      mockedUrlResource.when(() -> UrlResource.from(filePath.toUri())).thenReturn(resource);

      var loadedResource = nasFileService.loadFile(filename);

      assertNotNull(loadedResource);
      assertEquals(resource.getURI(), loadedResource.getURI());
    }
  }

  @Test
  void loadFile_fileDoesNotExist() {
    var filename = "nonExistentFile.txt";
    when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/nas");

    assertThrows(FileNotFoundException.class, () -> nasFileService.loadFile(filename));
  }

  @TempDir
  Path tempDir;

  @Test
  void saveFile_validInput() throws IOException {
    var filename = "testFile.txt";
    var content = "test content".getBytes();
    var targetDirectory = tempDir.toAbsolutePath().normalize();
    var filePath = targetDirectory.resolve(filename).normalize();

    when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());

    nasFileService.saveFile(filename, content);

    assertTrue(Files.exists(filePath));
    assertArrayEquals(content, Files.readAllBytes(filePath));
  }

  @Test
  void saveFile_invalidFileName() {
    var filename = "../invalidFile.txt";
    var content = "test content".getBytes();

    when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());

    assertThrows(IOException.class, () -> nasFileService.saveFile(filename, content));
  }

  @Test
  void saveFile_pathOutsideNasDirectory() {
    var content = "test content".getBytes();
    var targetDirectory = tempDir.toAbsolutePath().normalize();
    var filePath = targetDirectory.resolve("../outsideDir/testFile.txt").normalize();

    when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());

    assertThrows(IOException.class, () -> nasFileService.saveFile(filePath.toString(), content));
  }
}