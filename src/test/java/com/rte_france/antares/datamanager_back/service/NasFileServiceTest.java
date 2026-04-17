package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NasFileServiceTest {

  @Mock
  private TimeSeriesReader timeSeriesReader;

  @Mock
  private TimeSeriesWriter timeSeriesWriter;

  @Mock
  private TimeSeriesMatrix timeSeriesMatrix;

  @InjectMocks
  private NasFileService nasFileService;

  @Mock
  private AntaresDataManagerProperties antaresDataManagerProperties;

  private static final String OUTPUT_DIRECTORY = "output";

  @TempDir
  private Path tempDir;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
  }

  @Test
  void loadFile_fileExists() throws IOException {
    var filename = "testFile.txt";
    var filePath = Path.of("/nas").resolve(filename);
    when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/nas");

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
    when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/nas");

    assertThrows(TechnicalException.class, () -> nasFileService.loadFile(filename));
  }

  @Test
  void saveFile_validInput() throws IOException {
    var filename = "validFile.txt";
    var content = "test content".getBytes();
    var targetDirectory = tempDir.resolve("output");
    Files.createDirectories(targetDirectory);

    when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
    when(antaresDataManagerProperties.getOutputLoadDirectory()).thenReturn(OUTPUT_DIRECTORY);

    nasFileService.saveFile(filename, content,OUTPUT_DIRECTORY);

    var savedFile = targetDirectory.resolve(filename);
    assertTrue(Files.exists(savedFile));
    assertArrayEquals(content, Files.readAllBytes(savedFile));
  }

  @Test
  void saveFile_nullFilename() {
    var content = "test content".getBytes();

    assertThrows(NullPointerException.class, () -> nasFileService.saveFile(null, content,OUTPUT_DIRECTORY));
  }

  @Test
  void saveFile_nullContent() {
    var filename = "validFile.txt";

    assertThrows(NullPointerException.class, () -> nasFileService.saveFile(filename, null,OUTPUT_DIRECTORY));
  }

  @Test
  void saveFile_invalidFilename() {
    var filename = "../invalidFile.txt";
    var content = "test content".getBytes();

    when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());

    assertThrows(TechnicalException.class, () -> nasFileService.saveFile(filename, content, OUTPUT_DIRECTORY));
  }

  @Test
  void saveFile_pathOutsideNasDirectory() {
    var filename = "../outsideDir/testFile.txt";
    var content = "test content".getBytes();

    when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
    when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectory");
    when(antaresDataManagerProperties.getLoadDirectory()).thenReturn("load");
    when(antaresDataManagerProperties.getOutputLoadDirectory()).thenReturn("output");

    assertThrows(TechnicalException.class, () -> nasFileService.saveFile(filename, content, OUTPUT_DIRECTORY));
  }


  @Test
  void saveMatrixToNas_fromMatrix_validInput() throws IOException {
    when(timeSeriesWriter.writeToByteArray(any(TimeSeriesMatrix.class))).thenReturn("matrix content".getBytes());
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");
    when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());

    String result = nasFileService.saveMatrixToNas(timeSeriesMatrix, "baseName", OUTPUT_DIRECTORY);

    assertNotNull(result);
    assertTrue(result.startsWith("baseName."));
    assertTrue(result.endsWith(".arrow"));
    
    Path savedFile = tempDir.resolve(OUTPUT_DIRECTORY).resolve(result);
    assertTrue(Files.exists(savedFile));
    assertArrayEquals("matrix content".getBytes(), Files.readAllBytes(savedFile));
  }

  @Test
  void saveMatrixToNas_fromMatrix_nullMatrix() {
    assertThrows(NullPointerException.class, () -> nasFileService.saveMatrixToNas((TimeSeriesMatrix) null, "baseName", OUTPUT_DIRECTORY));
  }

  @Test
  void saveMatrixToNas_fromMatrix_nullBaseName() {
    assertThrows(NullPointerException.class, () -> nasFileService.saveMatrixToNas(timeSeriesMatrix, null, OUTPUT_DIRECTORY));
  }

  @Test
  void readMatrix_txtFile_returnsMatrix() throws Exception {
    Path txtFile = tempDir.resolve("series.txt");
    Files.writeString(txtFile, "col\n1.0\n2.0\n");
    when(timeSeriesReader.readFromTxt(txtFile)).thenReturn(timeSeriesMatrix);

    TimeSeriesMatrix result = nasFileService.readMatrix(txtFile, null);

    assertNotNull(result);
    assertEquals(timeSeriesMatrix, result);
    verify(timeSeriesReader).readFromTxt(txtFile);
  }

  @Test
  void readMatrix_xlsxFile_returnsMatrix() throws Exception {
    Path xlsxFile = tempDir.resolve("series.xlsx");
    Files.writeString(xlsxFile, "dummy");
    when(timeSeriesReader.readFromXlsx(xlsxFile, "2030")).thenReturn(timeSeriesMatrix);

    TimeSeriesMatrix result = nasFileService.readMatrix(xlsxFile, "2030");

    assertNotNull(result);
    assertEquals(timeSeriesMatrix, result);
    verify(timeSeriesReader).readFromXlsx(xlsxFile, "2030");
  }

  @Test
  void readMatrix_unsupportedFormat_throwsTechnicalException() throws Exception {
    Path unsupportedFile = tempDir.resolve("series.json");
    Files.writeString(unsupportedFile, "{}");

    assertThrows(TechnicalException.class, () -> nasFileService.readMatrix(unsupportedFile, null));
  }

  @Test
  void readMatrix_nullInputPath_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> nasFileService.readMatrix(null, null));
  }

  @Test
  void saveFile_absoluteOutputDirectory_throwsTechnicalException() {
    String absoluteOutputDir = tempDir.toAbsolutePath().toString();
    byte[] data = "data".getBytes();
    TechnicalException ex = assertThrows(
            TechnicalException.class,
            () -> nasFileService.saveFile("valid.txt", data, absoluteOutputDir)
    );


    assertTrue(ex.getMessage().contains("Output directory must be a relative path"));
   }

  @Test
  void saveMatrixToNas_fromPathTxt_savesSerializedMatrix() throws Exception {
    Path input = tempDir.resolve("input.txt");
    Files.writeString(input, "x");
    when(timeSeriesReader.readFromTxt(input)).thenReturn(timeSeriesMatrix);
    when(timeSeriesWriter.writeToByteArray(timeSeriesMatrix)).thenReturn("bytes".getBytes());
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");

    String savedName = nasFileService.saveMatrixToNas(input, OUTPUT_DIRECTORY, null);

    assertNotNull(savedName);
    assertTrue(Files.exists(tempDir.resolve(OUTPUT_DIRECTORY).resolve(savedName)));
    verify(timeSeriesReader).readFromTxt(input);
  }

  @Test
  void saveMatrixToNas_fromPathUnsupported_throwsTechnicalException() throws Exception {
    Path input = tempDir.resolve("input.json");
    Files.writeString(input, "{}");

    TechnicalException ex = assertThrows(
            TechnicalException.class,
            () -> nasFileService.saveMatrixToNas(input, OUTPUT_DIRECTORY, null)
    );

    assertTrue(ex.getMessage().contains("Failed to read time series matrix from file"));
  }

  @Test
  void saveMatrixToNas_whenReaderFails_wrapsTechnicalException() throws Exception {
    Path input = tempDir.resolve("input.xlsx");
    Files.writeString(input, "dummy");
    when(timeSeriesReader.readFromXlsx(input, "2030")).thenThrow(new IOException("broken xlsx"));

    TechnicalException ex = assertThrows(
            TechnicalException.class,
            () -> nasFileService.saveMatrixToNas(input, OUTPUT_DIRECTORY, "2030")
    );

    assertTrue(ex.getMessage().contains("Failed to read time series matrix from file"));
  }

  @Test
  void saveMatrixBytesToNas_shouldCreateArrowFile() throws Exception {
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");

    String savedName = nasFileService.saveMatrixBytesToNas("raw".getBytes(), "constraints.csv", OUTPUT_DIRECTORY);

    assertTrue(savedName.startsWith("constraints.csv."));
    assertTrue(savedName.endsWith(".arrow"));
    assertTrue(Files.exists(tempDir.resolve(OUTPUT_DIRECTORY).resolve(savedName)));
  }

  @Test
  void readMatrix_whenReaderThrowsRuntime_wrapsTechnicalException() throws Exception {
    Path txtFile = tempDir.resolve("series.csv");
    Files.writeString(txtFile, "col\n1\n");
    when(timeSeriesReader.readFromTxt(txtFile)).thenThrow(new IllegalStateException("reader failed"));

    TechnicalException ex = assertThrows(TechnicalException.class, () -> nasFileService.readMatrix(txtFile, null));
    assertTrue(ex.getMessage().contains("Failed to read time series matrix from file"));
  }
}

