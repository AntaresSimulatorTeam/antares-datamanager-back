package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
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
import java.util.List;

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
    assertThrows(NullPointerException.class, () -> nasFileService.saveMatrixToNas(null, "baseName", OUTPUT_DIRECTORY));
  }

  @Test
  void saveMatrixToNas_fromMatrix_nullBaseName() {
    assertThrows(NullPointerException.class, () -> nasFileService.saveMatrixToNas(timeSeriesMatrix, null, OUTPUT_DIRECTORY));
  }

  @Test
  void readMatrix_txtFile_returnsMatrix() throws Exception {
    Path txtFile = tempDir.resolve("series.txt");
    Files.writeString(txtFile, "col\n1.0\n2.0\n");
    when(timeSeriesReader.readFromTxt(eq(txtFile), anyBoolean())).thenReturn(timeSeriesMatrix);

    TimeSeriesMatrix result = nasFileService.readMatrix(txtFile, null);

    assertNotNull(result);
    assertEquals(timeSeriesMatrix, result);
    verify(timeSeriesReader).readFromTxt(txtFile, true);
  }

  @Test
  void readMatrix_xlsxFile_returnsMatrix() throws Exception {
    Path xlsxFile = tempDir.resolve("series.xlsx");
    Files.writeString(xlsxFile, "dummy");
    when(timeSeriesReader.readFromXlsx(xlsxFile, "2030", true)).thenReturn(timeSeriesMatrix);

    TimeSeriesMatrix result = nasFileService.readMatrix(xlsxFile, "2030");

    assertNotNull(result);
    assertEquals(timeSeriesMatrix, result);
    verify(timeSeriesReader).readFromXlsx(xlsxFile, "2030", true);
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
    when(timeSeriesReader.readFromTxt(eq(input), anyBoolean())).thenReturn(timeSeriesMatrix);
    when(timeSeriesWriter.writeToByteArray(timeSeriesMatrix)).thenReturn("bytes".getBytes());
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");

    String savedName = nasFileService.readAndSaveMatrixToNas(input, OUTPUT_DIRECTORY, null, true);

    assertNotNull(savedName);
    assertTrue(Files.exists(tempDir.resolve(OUTPUT_DIRECTORY).resolve(savedName)));
    verify(timeSeriesReader).readFromTxt(input, true);
  }

  @Test
  void saveMatrixToNas_fromPathUnsupported_throwsTechnicalException() throws Exception {
    Path input = tempDir.resolve("input.json");
    Files.writeString(input, "{}");

    TechnicalException ex = assertThrows(
            TechnicalException.class,
            () -> nasFileService.readAndSaveMatrixToNas(input, OUTPUT_DIRECTORY, null, true)
    );

    assertTrue(ex.getMessage().contains("Unsupported input format"));
  }

  @Test
  void saveMatrixToNas_whenReaderFails_wrapsTechnicalException() throws Exception {
    Path input = tempDir.resolve("input.xlsx");
    Files.writeString(input, "dummy");
    when(timeSeriesReader.readFromXlsx(input, "2030", true)).thenThrow(new IOException("broken xlsx"));

    TechnicalException ex = assertThrows(
            TechnicalException.class,
            () -> nasFileService.readAndSaveMatrixToNas(input, OUTPUT_DIRECTORY, "2030", true)
    );

    assertTrue(ex.getMessage().contains("Failed to read time series matrix from file"));
    assertTrue(ex.getMessage().contains("input.xlsx"));
    assertTrue(ex.getMessage().contains("horizon: 2030"));
  }

  @Test
  void saveMatrixToNas_whenReaderThrowsBusinessException_propagatesAsIs() throws Exception {
    Path input = tempDir.resolve("trajectoire.xlsx");
    Files.writeString(input, "dummy");
    BusinessException businessEx = BusinessException.builder()
            .message("Horizon {0} does not exist in file: {1}")
            .errorMessageArguments(List.of("2030", "trajectoire.xlsx"))
            .httpStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
            .build();
    when(timeSeriesReader.readFromXlsx(input, "2030", true)).thenThrow(businessEx);

    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> nasFileService.readAndSaveMatrixToNas(input, OUTPUT_DIRECTORY, "2030", true)
    );

    assertSame(businessEx, ex);
  }

  @Test
  void readMatrix_whenReaderThrowsBusinessException_propagatesAsIs() throws Exception {
    Path input = tempDir.resolve("trajectoire.xlsx");
    Files.writeString(input, "dummy");
    BusinessException businessEx = BusinessException.builder()
            .message("Horizon {0} does not exist in file: {1}")
            .errorMessageArguments(List.of("2030", "trajectoire.xlsx"))
            .httpStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
            .build();
    when(timeSeriesReader.readFromXlsx(input, "2030", true)).thenThrow(businessEx);

    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> nasFileService.readMatrix(input, "2030")
    );

    assertSame(businessEx, ex);
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
    when(timeSeriesReader.readFromTxt(eq(txtFile), anyBoolean())).thenThrow(new IllegalStateException("reader failed"));

    TechnicalException ex = assertThrows(TechnicalException.class, () -> nasFileService.readMatrix(txtFile, null));
    assertTrue(ex.getMessage().contains("Failed to read time series matrix from file"));
  }

  // ── saveFile ──────────────────────────────────────────────────────────────

  @Test
  void saveFile_blankFilename_throwsTechnicalException() {
    assertThrows(TechnicalException.class,
            () -> nasFileService.saveFile("   ", "content".getBytes(), OUTPUT_DIRECTORY));
  }

  // ── saveMatrixToNas(Path, String, String) ────────────────────────────────

  @Test
  void saveMatrixToNas_fromPathXlsx_validInput() throws Exception {
    Path input = tempDir.resolve("data.xlsx");
    Files.writeString(input, "dummy");
    when(timeSeriesReader.readFromXlsx(input, "2030", true)).thenReturn(timeSeriesMatrix);
    when(timeSeriesWriter.writeToByteArray(timeSeriesMatrix)).thenReturn("bytes".getBytes());
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");

    String savedName = nasFileService.readAndSaveMatrixToNas(input, OUTPUT_DIRECTORY, "2030", true);

    assertNotNull(savedName);
    assertTrue(savedName.startsWith("data."));
    assertTrue(savedName.endsWith(".arrow"));
    assertTrue(Files.exists(tempDir.resolve(OUTPUT_DIRECTORY).resolve(savedName)));
    verify(timeSeriesReader).readFromXlsx(input, "2030", true);
  }

  @Test
  void saveMatrixToNas_fromPathCsv_savesSerializedMatrix() throws Exception {
    Path input = tempDir.resolve("input.csv");
    Files.writeString(input, "x");
    when(timeSeriesReader.readFromTxt(eq(input), anyBoolean())).thenReturn(timeSeriesMatrix);
    when(timeSeriesWriter.writeToByteArray(timeSeriesMatrix)).thenReturn("bytes".getBytes());
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");

    String savedName = nasFileService.readAndSaveMatrixToNas(input, OUTPUT_DIRECTORY, null, true);

    assertNotNull(savedName);
    assertTrue(Files.exists(tempDir.resolve(OUTPUT_DIRECTORY).resolve(savedName)));
    verify(timeSeriesReader).readFromTxt(input, true);
  }

  @Test
  void saveMatrixToNas_2argVariant_delegates() throws Exception {
    Path input = tempDir.resolve("input.txt");
    Files.writeString(input, "x");
    when(timeSeriesReader.readFromTxt(eq(input), anyBoolean())).thenReturn(timeSeriesMatrix);
    when(timeSeriesWriter.writeToByteArray(timeSeriesMatrix)).thenReturn("bytes".getBytes());
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");

    String savedName = nasFileService.readAndSaveMatrixToNas(input, OUTPUT_DIRECTORY, null, true);

    assertNotNull(savedName);
    verify(timeSeriesReader).readFromTxt(input, true);
  }

  @Test
  void saveMatrixToNas_whenReaderFailsWithNoSheetName_noHorizonInfoInMessage() throws Exception {
    Path input = tempDir.resolve("input.xlsx");
    Files.writeString(input, "dummy");
    when(timeSeriesReader.readFromXlsx(input, null, true)).thenThrow(new IOException("broken xlsx"));

    TechnicalException ex = assertThrows(TechnicalException.class,
            () -> nasFileService.readAndSaveMatrixToNas(input, OUTPUT_DIRECTORY, null, true));

    assertTrue(ex.getMessage().contains("Failed to read time series matrix from file"));
    assertTrue(ex.getMessage().contains("input.xlsx"));
    assertFalse(ex.getMessage().contains("horizon:"));
  }

  // ── readMatrix ────────────────────────────────────────────────────────────

  @Test
  void readMatrix_csvFile_returnsMatrix() throws Exception {
    Path csvFile = tempDir.resolve("series.csv");
    Files.writeString(csvFile, "col\n1.0\n");
    when(timeSeriesReader.readFromTxt(eq(csvFile), anyBoolean())).thenReturn(timeSeriesMatrix);

    TimeSeriesMatrix result = nasFileService.readMatrix(csvFile, null);

    assertEquals(timeSeriesMatrix, result);
    verify(timeSeriesReader).readFromTxt(csvFile, true);
  }

  @Test
  void readMatrix_xlsxFailure_includesHorizonInfoInMessage() throws Exception {
    Path xlsxFile = tempDir.resolve("data.xlsx");
    Files.writeString(xlsxFile, "dummy");
    when(timeSeriesReader.readFromXlsx(xlsxFile, "2030", true)).thenThrow(new IOException("corrupt"));

    TechnicalException ex = assertThrows(TechnicalException.class,
            () -> nasFileService.readMatrix(xlsxFile, "2030"));

    assertTrue(ex.getMessage().contains("Failed to read time series matrix from file"));
    assertTrue(ex.getMessage().contains("data.xlsx"));
    assertTrue(ex.getMessage().contains("horizon: 2030"));
  }

  // ── saveMatrixToNas(TimeSeriesMatrix, ...) ────────────────────────────────

  @Test
  void saveMatrixToNas_fromMatrix_baseNameAlreadyHasWriterExtension_stripsAndReplaces() throws IOException {
    when(timeSeriesWriter.writeToByteArray(any(TimeSeriesMatrix.class))).thenReturn("data".getBytes());
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");

    String savedName = nasFileService.saveMatrixToNas(timeSeriesMatrix, "fileTStest.arrow", OUTPUT_DIRECTORY);

    // Extension should not be doubled: "fileTStest.<uuid>.arrow", not "fileTStest.arrow.<uuid>.arrow"
    assertTrue(savedName.startsWith("fileTStest."));
    assertTrue(savedName.endsWith(".arrow"));
    assertFalse(savedName.startsWith("fileTStest.arrow."));
  }

  @Test
  void saveMatrixToNas_fromMatrix_baseNameHasSingleQuotes_stripsQuotes() throws IOException {
    when(timeSeriesWriter.writeToByteArray(any(TimeSeriesMatrix.class))).thenReturn("data".getBytes());
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");

    String savedName = nasFileService.saveMatrixToNas(timeSeriesMatrix, "'quoted file.csv'", OUTPUT_DIRECTORY);

    // Should be "quoted file.csv.<uuid>.arrow"
    assertTrue(savedName.startsWith("quoted file.csv."));
    assertTrue(savedName.endsWith(".arrow"));
    assertFalse(savedName.startsWith("'"));
  }

  // ── saveMatrixBytesToNas ──────────────────────────────────────────────────

  @Test
  void saveMatrixBytesToNas_nullData_throwsNullPointerException() {
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");
    assertThrows(NullPointerException.class,
            () -> nasFileService.saveMatrixBytesToNas(null, "base.csv", OUTPUT_DIRECTORY));
  }

  @Test
  void saveMatrixBytesToNas_nullBaseName_throwsNullPointerException() {
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");
    assertThrows(NullPointerException.class,
            () -> nasFileService.saveMatrixBytesToNas("data".getBytes(), null, OUTPUT_DIRECTORY));
  }

  @Test
  void deleteFile_validInput_fileDeleted() throws IOException {
    String filename = "toDelete.txt";
    Path targetDir = tempDir.resolve(OUTPUT_DIRECTORY);
    Files.createDirectories(targetDir);
    Path file = targetDir.resolve(filename);
    Files.writeString(file, "content");

    nasFileService.deleteFile(OUTPUT_DIRECTORY, filename);

    assertFalse(Files.exists(file));
  }

  @Test
  void deleteFile_invalidInput_doesNothing() {
    // Should not throw and should log warning (verified manually or via log capturing if configured)
    nasFileService.deleteFile(null, "file.txt");
    nasFileService.deleteFile("", "file.txt");
    nasFileService.deleteFile(OUTPUT_DIRECTORY, null);
    nasFileService.deleteFile(OUTPUT_DIRECTORY, "");
    nasFileService.deleteFile(OUTPUT_DIRECTORY, "../outside.txt");
    nasFileService.deleteFile("/absolute", "file.txt");
  }

  @Test
  void readFile_validInput_returnsBytes() throws IOException {
    String filename = "toRead.txt";
    byte[] content = "some content".getBytes();
    Path targetDir = tempDir.resolve(OUTPUT_DIRECTORY);
    Files.createDirectories(targetDir);
    Path file = targetDir.resolve(filename);
    Files.write(file, content);

    byte[] result = nasFileService.readFile(OUTPUT_DIRECTORY, filename);

    assertArrayEquals(content, result);
  }

  @Test
  void readFile_fileDoesNotExist_throwsIOException() {
    assertThrows(IOException.class, () -> nasFileService.readFile(OUTPUT_DIRECTORY, "nonExistent.txt"));
  }

  @Test
  void deleteFile_fileDoesNotExist_doesNotThrow() {
    nasFileService.deleteFile(OUTPUT_DIRECTORY, "nonExistent.txt");
    // Should not throw
  }

  @Test
  void saveMatrixToNas_withHeaderFalse_callsReaderWithFalse() throws IOException {
    Path input = tempDir.resolve("input.txt");
    Files.writeString(input, "1.0");
    when(timeSeriesReader.readFromTxt(eq(input), eq(false))).thenReturn(timeSeriesMatrix);
    when(timeSeriesWriter.writeToByteArray(timeSeriesMatrix)).thenReturn("bytes".getBytes());
    when(timeSeriesWriter.getDefaultFileExtension()).thenReturn("arrow");

    nasFileService.readAndSaveMatrixToNas(input, OUTPUT_DIRECTORY, null, false);

    verify(timeSeriesReader).readFromTxt(input, false);
  }

  @Test
  void readMatrix_withHeaderFalse_callsReaderWithFalse() throws IOException {
    Path input = tempDir.resolve("input.csv");
    when(timeSeriesReader.readFromTxt(eq(input), eq(false))).thenReturn(timeSeriesMatrix);

    TimeSeriesMatrix result = nasFileService.readMatrix(input, null, false);

    assertEquals(timeSeriesMatrix, result);
    verify(timeSeriesReader).readFromTxt(input, false);
  }

  @Test
  void loadFile_notReadable_throwsTechnicalException() throws IOException {
    var filename = "unreadable.txt";
    var filePath = Path.of("/nas").resolve(filename);
    when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/nas");

    var resource = mock(UrlResource.class);
    when(resource.exists()).thenReturn(true);
    when(resource.isReadable()).thenReturn(false);
    when(resource.getURI()).thenReturn(filePath.toUri());

    try (var mockedUrlResource = mockStatic(UrlResource.class)) {
      mockedUrlResource.when(() -> UrlResource.from(filePath.toUri())).thenReturn(resource);

      assertThrows(TechnicalException.class, () -> nasFileService.loadFile(filename));
    }
  }

  @Test
  void saveFile_nasDirectoryNotExists_createsDirectories() throws IOException {
    String filename = "file.txt";
    byte[] content = "content".getBytes();
    String subDir = "new/sub/dir";
    Path fullTargetDir = tempDir.resolve(subDir);
    // Ensure it doesn't exist
    assertFalse(Files.exists(fullTargetDir));

    nasFileService.saveFile(filename, content, subDir);

    assertTrue(Files.exists(fullTargetDir.resolve(filename)));
  }

  @Test
  void readFile_invalidInput_throwsException() {
    assertThrows(IOException.class, () -> nasFileService.readFile(null, "f.txt"));
    assertThrows(IOException.class, () -> nasFileService.readFile(OUTPUT_DIRECTORY, "../f.txt"));
    assertThrows(IOException.class, () -> nasFileService.readFile("/absolute", "f.txt"));
  }
}

