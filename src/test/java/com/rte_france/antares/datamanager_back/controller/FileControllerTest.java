package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileControllerTest {

  @InjectMocks
  private FileController fileController;

  @Mock
  private NasFileService nasFileService;

  @Mock
  private AntaresDataManagerProperties properties;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void getFile_fileExists() throws Exception {
    var filename = "testFile.txt";
    var resource = mock(Resource.class);
    when(nasFileService.loadFile(filename)).thenReturn(resource);

    var response = fileController.getFile(filename);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(resource, response.getBody());
    assertEquals("attachment; filename=\"" + filename + "\"", response.getHeaders().getFirst("Content-Disposition"));
  }

  @Test
  void getFile_fileDoesNotExist() throws Exception {
    var filename = "nonExistentFile.txt";
    when(nasFileService.loadFile(filename)).thenThrow(new RuntimeException("File not found"));

    var response = fileController.getFile(filename);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void uploadFile_validFile() throws IOException {
    var filename = "testFile.txt";
    var content = "test content".getBytes();
    var file = new MockMultipartFile("file", filename, "text/plain", content);
    when(properties.getNasDirectory()).thenReturn("/nas");

    var response = fileController.uploadFile(file);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("Fichier uploadé avec succès !", response.getBody());
    verify(nasFileService, times(1)).saveFile(any(String.class), eq(content), any(String.class));
  }

  @Test
  void uploadFile_invalidFileName() throws IOException {
    var filename = "../invalidFile.txt";
    var content = "test content".getBytes();
    var file = new MockMultipartFile("file", filename, "text/plain", content);

    var response = fileController.uploadFile(file);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Invalid file name: " + filename, response.getBody());
    verify(nasFileService, never()).saveFile(any(String.class), any(), any(String.class));
  }

  @Test
  void uploadFile_nullFileName() throws IOException {
    var file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(null);
    when(properties.getNasDirectory()).thenReturn("/nas-test");
    var response = fileController.uploadFile(file);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Invalid file name: null", response.getBody());
    verify(nasFileService, never()).saveFile(any(String.class), any(), any(String.class));
  }

  @Test
  void uploadFile_pathOutsideNasDirectory() throws IOException {
    var filename = "/test/testFile.txt";
    var content = "test content".getBytes();
    var file = new MockMultipartFile("file", filename, "text/plain", content);
    when(properties.getNasDirectory()).thenReturn("/nas");

    TechnicalException exception = assertThrows(TechnicalException.class, () -> fileController.uploadFile(file));

    assertTrue(exception.getMessage().contains("Path outside of target: " + filename));
    verify(nasFileService, never()).saveFile(any(String.class), any(), any(String.class));
  }
}