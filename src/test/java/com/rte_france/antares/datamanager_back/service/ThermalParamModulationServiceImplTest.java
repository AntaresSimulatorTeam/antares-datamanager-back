package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalSpecificFileProcessorService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalParamModulationServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThermalParamModulationServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private ThermalSpecificFileProcessorService thermalSpecificFileProcessorService;

    @Mock
    private NasFileService nasFileService;

    @Mock
    private AntaressDataManagerProperties antaressDataManagerProperties;

    @InjectMocks
    private ThermalParamModulationServiceImpl thermalParamModulationService;


    @Test
    void processThermalModulationParameterFile_shouldSaveNewTrajectoryWhenNoExistingTrajectory(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "modulation_trajectory";
        String paramModulationDir = "thermal";

        Path trajectoryPath = tempDir.resolve(paramModulationDir).resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);

        String horizon = "2025-2026";
        List<ThermalModulationParameterEntity> entities = List.of(new ThermalModulationParameterEntity());

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = thermalParamModulationService.processThermalModulationParameterFile(
                trajectoryPath, horizon, entities, TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER);

        assertNotNull(result);
        assertEquals("THERMAL_TECHNICAL_MODULATION_PARAMETER", result.getType());
        assertEquals(1, result.getVersion());
        verify(trajectoryRepository).save(any());
    }

    @Test
    void createSplitCmAndMrParamFiles_shouldSplitCMandMRFiles() throws IOException {
        // GIVEN
        StudyEntity study = new StudyEntity();
        study.setId(10);
        study.setHorizon("2030");

        Path cm = Path.of("CM_test.csv");
        Path mr = Path.of("MR_test.csv");

        study.setTrajectories(Set.of(
                createTrajectory(cm),
                createTrajectory(mr)
        ));

        ThermalParamModulationServiceImpl spy = Mockito.spy(thermalParamModulationService);

        doReturn(List.of(cm, mr)).when(spy).getParamModulationTsFiles(any());

        when(thermalSpecificFileProcessorService.getListClusterByAreaForSpecificParam("2030", 10, false))
                .thenReturn(Set.of("ClusterA"));
        when(thermalSpecificFileProcessorService.getListClusterByAreaForSpecificParam("2030", 10, true))
                .thenReturn(Set.of("ClusterB"));

        Path cmOut = Path.of("CM_A.csv");
        Path mrOut = Path.of("MR_B.csv");

        doReturn(List.of(cmOut)).when(spy).splitCmAndMrParamFiles(cm, Set.of("ClusterA"));
        doReturn(List.of(mrOut)).when(spy).splitCmAndMrParamFiles(mr, Set.of("ClusterB"));

        // WHEN
        List<Path> result = spy.createSplitCmAndMrParamFiles(study);

        // THEN
        assertEquals(2, result.size());
        assertTrue(result.contains(cmOut));
        assertTrue(result.contains(mrOut));

        verify(spy).splitCmAndMrParamFiles(cm, Set.of("ClusterA"));
        verify(spy).splitCmAndMrParamFiles(mr, Set.of("ClusterB"));
    }

    @Test
    void createSplitCmAndMrParamFiles_shouldReturnEmpty_whenNoClusters() throws IOException {
        StudyEntity study = new StudyEntity();
        study.setId(5);
        study.setHorizon("2040");

        Path cm = Path.of("CM_test.csv");
        study.setTrajectories(Set.of(createTrajectory(cm)));

        ThermalParamModulationServiceImpl spy = Mockito.spy(thermalParamModulationService);
        doReturn(List.of(cm)).when(spy).getParamModulationTsFiles(any());

        when(thermalSpecificFileProcessorService.getListClusterByAreaForSpecificParam("2040", 5, false))
                .thenReturn(Collections.emptySet());

        List<Path> result = spy.createSplitCmAndMrParamFiles(study);

        assertTrue(result.isEmpty());
        verify(spy, never()).splitCmAndMrParamFiles(any(), any());
    }

    @Test
    void createSplitCmAndMrParamFiles_shouldThrowTechnicalException_onSplitError() throws IOException {
        StudyEntity study = new StudyEntity();
        study.setId(22);
        study.setHorizon("2035");

        Path cm = Path.of("CM_test.csv");
        study.setTrajectories(Set.of(createTrajectory(cm)));

        ThermalParamModulationServiceImpl spy = Mockito.spy(thermalParamModulationService);

        doReturn(List.of(cm)).when(spy).getParamModulationTsFiles(any());
        when(thermalSpecificFileProcessorService.getListClusterByAreaForSpecificParam("2035", 22, false))
                .thenReturn(Set.of("ClustX"));

        doThrow(new IOException("IO ERROR"))
                .when(spy).splitCmAndMrParamFiles(cm, Set.of("ClustX"));

        assertThrows(TechnicalException.class, () -> spy.createSplitCmAndMrParamFiles(study));
    }

    @Test
    void createSplitCmAndMrParamFiles_shouldReturnEmpty_whenNoTrajectories() {
        StudyEntity study = new StudyEntity();
        study.setTrajectories(Collections.emptySet());

        ThermalParamModulationServiceImpl spy = Mockito.spy(thermalParamModulationService);

        doReturn(Collections.emptyList()).when(spy).getParamModulationTsFiles(any());

        List<Path> result = spy.createSplitCmAndMrParamFiles(study);

        assertTrue(result.isEmpty());
    }


    private TrajectoryEntity createTrajectory(Path p) {
        TrajectoryEntity t = new TrajectoryEntity();
        ThermalModulationParameterEntity param = new ThermalModulationParameterEntity();
        param.setTsName(p.toString());
        t.setThermalModulationParameters(List.of(param));
        t.setType(TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER.name());
        return t;
    }


    @Test
    void testGetParamModulationTsFiles_nominal() throws IOException {
        TrajectoryEntity t1 = mock(TrajectoryEntity.class);
        when(t1.getType()).thenReturn(TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER.name());

        ThermalModulationParameterEntity p1 = mock(ThermalModulationParameterEntity.class);
        when(p1.getTsName()).thenReturn("ts1.csv");

        when(t1.getThermalModulationParameters()).thenReturn(List.of(p1));

        TrajectoryEntity t2 = mock(TrajectoryEntity.class);
        when(t2.getType()).thenReturn("THERMAL_TECHNICAL_MODULATION_PARAMETER");

        ThermalModulationParameterEntity p2 = mock(ThermalModulationParameterEntity.class);
        when(p2.getTsName()).thenReturn(null);

        when(t2.getThermalModulationParameters()).thenReturn(List.of(p2));


        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaressDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn("thermal_modulation");

        Path base = tempDir.resolve("trajectories").resolve("thermal_modulation");
        Files.createDirectories(base);
        Path tsFile = base.resolve("ts1.csv");
        Files.createFile(tsFile);

        List<Path> result = thermalParamModulationService.getParamModulationTsFiles(List.of(t1, t2));

        assertEquals(1, result.size());
        assertEquals(tsFile.toRealPath(), result.get(0).toRealPath());
    }

    @Test
    void testCreateWriters_nominal() throws IOException {
        String[] columns = {"col0", "col1", "AreaA", "AreaB", "AreaC"};
        Path inputFile = tempDir.resolve("testfile.csv");

        List<Path> generatedFiles = new ArrayList<>();
        Set<String> clusters = Set.of("areaa", "areac");

        Map<Integer, BufferedWriter> writers = thermalParamModulationService.createWriters(
                columns,
                inputFile,
                tempDir,
                generatedFiles,
                clusters
        );

        // Vérifie les index créés (AreaA et AreaC => index 2 et 4)
        assertEquals(2, writers.size());
        assertTrue(writers.containsKey(2));
        assertTrue(writers.containsKey(4));

        // Vérifie les fichiers créés
        assertEquals(2, generatedFiles.size());
        assertTrue(Files.exists(tempDir.resolve("testfile_AreaA.csv")));
        assertTrue(Files.exists(tempDir.resolve("testfile_AreaC.csv")));

        // Ferme les écrivains utilisés dans le test
        thermalParamModulationService.closeAll(writers);
    }

    @Test
    void testCreateWriters_ignoreUnknownClusters() throws IOException {
        String[] columns = {"col0", "col1", "X", "Y"};
        Path inputFile = tempDir.resolve("file.csv");

        List<Path> generatedFiles = new ArrayList<>();
        Set<String> clusters = Set.of("zzz"); // Aucun match

        Map<Integer, BufferedWriter> writers = thermalParamModulationService.createWriters(
                columns,
                inputFile,
                tempDir,
                generatedFiles,
                clusters
        );

        assertTrue(writers.isEmpty());
        assertTrue(generatedFiles.isEmpty());
    }

    @Test
    void testCreateWriters_skipEmptyColumns() throws IOException {
        String[] columns = {"c0", "c1", " ", "AreaX"};
        Path inputFile = tempDir.resolve("t.csv");

        List<Path> generatedFiles = new ArrayList<>();
        Set<String> clusters = Set.of("areax");

        Map<Integer, BufferedWriter> writers = thermalParamModulationService.createWriters(
                columns,
                inputFile,
                tempDir,
                generatedFiles,
                clusters
        );

        // index 2 ignoré (vide)
        assertEquals(1, writers.size());
        assertTrue(writers.containsKey(3));

        thermalParamModulationService.closeAll(writers);
    }

    // ---------------------------------------------------
    //                TEST processFileLines
    // ---------------------------------------------------
    @Test
    void testProcessFileLines_nominal() throws IOException {
        // Prépare un jeu de données
        String[] columns = {"c0", "c1", "A", "B"};
        Path outfileA = tempDir.resolve("base_A.csv");
        Path outfileB = tempDir.resolve("base_B.csv");

        // Writers
        Map<Integer, BufferedWriter> writers = new HashMap<>();
        writers.put(2, Files.newBufferedWriter(outfileA));
        writers.put(3, Files.newBufferedWriter(outfileB));

        // Input simulé
        String data = """
                1;2;AA;BB
                x;y;CC;DD
                """;

        BufferedReader reader = new BufferedReader(new StringReader(data));

        thermalParamModulationService.processFileLines(reader, columns, writers);
        thermalParamModulationService.closeAll(writers);

        // Vérifie le contenu
        assertLinesMatch(List.of("AA", "CC"), Files.readAllLines(outfileA));
        assertLinesMatch(List.of("BB", "DD"), Files.readAllLines(outfileB));
    }

    @Test
    void testProcessFileLines_skipEmptyAndShortLines() throws IOException {
        String[] columns = {"c0", "c1", "A"};

        Path outA = tempDir.resolve("A.csv");
        Map<Integer, BufferedWriter> writers = Map.of(2, Files.newBufferedWriter(outA));

        String data = """
                
                onlyOneField
                1;2;VALUE
                """;

        BufferedReader reader = new BufferedReader(new StringReader(data));

        thermalParamModulationService.processFileLines(reader, columns, writers);
        thermalParamModulationService.closeAll(writers);

        assertLinesMatch(List.of("VALUE"), Files.readAllLines(outA));
    }

    @Test
    void testProcessFileLines_missingFieldFilledEmpty() throws IOException {
        String[] columns = {"c0", "c1", "A", "B"};

        Path outA = tempDir.resolve("A.csv");
        Path outB = tempDir.resolve("B.csv");

        Map<Integer, BufferedWriter> writers = new HashMap<>();
        writers.put(2, Files.newBufferedWriter(outA));
        writers.put(3, Files.newBufferedWriter(outB));

        String data = "1;2;ONLYA";

        BufferedReader reader = new BufferedReader(new StringReader(data));

        thermalParamModulationService.processFileLines(reader, columns, writers);
        thermalParamModulationService.closeAll(writers);

        assertLinesMatch(List.of("ONLYA"), Files.readAllLines(outA));
        assertLinesMatch(List.of(""), Files.readAllLines(outB)); // manque => ""
    }

    // ---------------------------------------------------
    //                TEST closeAll
    // ---------------------------------------------------
    @Test
    void testCloseAll_doesNotThrow() throws IOException {
        BufferedWriter bw1 = Files.newBufferedWriter(tempDir.resolve("a.txt"));
        BufferedWriter bw2 = Files.newBufferedWriter(tempDir.resolve("b.txt"));

        Map<Integer, BufferedWriter> writers = Map.of(1, bw1, 2, bw2);

        assertDoesNotThrow(() -> thermalParamModulationService.closeAll(writers));
    }

    // ---------------------------------------------------
    //                TEST getBaseName
    // ---------------------------------------------------
    @Test
    void testGetBaseName() {
        assertEquals("file", thermalParamModulationService.getBaseName(Path.of("file.csv")));
        assertEquals("noext", thermalParamModulationService.getBaseName(Path.of("noext")));
        assertEquals("archive.tar", thermalParamModulationService.getBaseName(Path.of("archive.tar.gz")));
    }

    @Test
    void testFileDoesNotExist_returnsEmpty() throws IOException {
        Path missingFile = tempDir.resolve("missing.csv");

        List<Path> result = thermalParamModulationService.splitCmAndMrParamFiles(missingFile, Set.of("area"));

        assertTrue(result.isEmpty());
    }

    @Test
    void testEmptyFile_returnsEmpty() throws IOException {

        Path file = tempDir.resolve("empty.csv");
        Files.writeString(file, "");

        List<Path> result = thermalParamModulationService.splitCmAndMrParamFiles(file, Set.of("a"));

        assertTrue(result.isEmpty());
    }

    @Test
    void testHeaderWithoutEnoughColumns_returnsEmpty() throws IOException {

        Path file = tempDir.resolve("badheader.csv");
        Files.writeString(file, "A;B");

        List<Path> result = thermalParamModulationService.splitCmAndMrParamFiles(file, Set.of("a"));

        assertTrue(result.isEmpty());
    }

    @Test
    void testNullClusterList_handledCorrectly() throws IOException {

        Path file = tempDir.resolve("test.csv");
        Files.writeString(file, "c0;c1;AreaA\n1;2;xx");

        List<Path> result = thermalParamModulationService.splitCmAndMrParamFiles(file, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void testNominalCase_generatesFilesAndWritesValues() throws IOException {

        Path file = tempDir.resolve("data.csv");

        String content = """
                C0;C1;Area1;Area2
                1;2;A1;B1
                3;4;A2;B2
                """;

        Files.writeString(file, content);

        List<Path> result = thermalParamModulationService.splitCmAndMrParamFiles(file, Set.of("area1"));

        // --- Vérification des fichiers générés ---
        assertEquals(1, result.size());
        Path out = result.get(0);

        assertTrue(Files.exists(out));
        assertEquals("data_Area1.csv", out.getFileName().toString());

        // --- Vérification du contenu ---
        List<String> lines = Files.readAllLines(out);
        assertEquals(List.of("A1", "A2"), lines);
    }

    @Test
    void testClusterValuesWithSpacesAndUppercase() throws IOException {

        Path file = tempDir.resolve("data2.csv");

        String content = """
                C0;C1;  ArEaX  ;Other
                1;2;XX;YY
                """;

        Files.writeString(file, content);

        List<Path> result = thermalParamModulationService.splitCmAndMrParamFiles(file, Set.of("areax"));

        assertEquals(1, result.size());
        Path out = result.get(0);

        assertTrue(Files.exists(out));
        assertLinesMatch(List.of("XX"), Files.readAllLines(out));
    }

    // java
    @Test
    void processThermalModulationSingleFile_addsThermalModulationParameterWhenValid() throws IOException {
        String trajectoryToUse = "trajectory_2025";
        String horizon = "2025-2026";
        Integer studyId = 1;
        String fileName = "valid.csv";

        Path baseDir = tempDir.resolve("trajectories");
        Files.createDirectories(baseDir);
        Path trajectoryFilePath = baseDir.resolve(fileName);
        Files.writeString(trajectoryFilePath, "DATE;HEURE;ClusterA;ClusterB\n2025-01-01;00:00;1;2");

        List<ThermalModulationParameterEntity> thermalModulationParameters = new ArrayList<>();
        Path file = trajectoryFilePath;

        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(thermalSpecificFileProcessorService.getListClusterByAreaForSpecificParam(horizon, studyId, true))
                .thenReturn(Set.of("clustera", "clusterb"));

        thermalParamModulationService.processThermalModulationSingleFile(
                trajectoryToUse, horizon, studyId, trajectoryFilePath, fileName, thermalModulationParameters, file, "MR");

        assertEquals(1, thermalModulationParameters.size());
        assertEquals(fileName, thermalModulationParameters.get(0).getTsName());
    }

    @Test
    void processThermalModulationSingleFile_throwsBusinessExceptionWhenClustersMissing() throws IOException {
        String trajectoryToUse = "trajectory_2025";
        String horizon = "2025-2026";
        Integer studyId = 1;
        String fileName = "missing_clusters.csv";

        Path baseDir = tempDir.resolve("trajectories");
        Files.createDirectories(baseDir);
        Path trajectoryFilePath = baseDir.resolve(fileName);
        Files.writeString(trajectoryFilePath, "DATE;HEURE;ClusterX\n2025-01-01;00:00;1");

        List<ThermalModulationParameterEntity> thermalModulationParameters = new ArrayList<>();
        Path file = trajectoryFilePath;

        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(thermalSpecificFileProcessorService.getListClusterByAreaForSpecificParam(horizon, studyId, true))
                .thenReturn(Set.of("clustera", "clusterb"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalParamModulationService.processThermalModulationSingleFile(
                        trajectoryToUse, horizon, studyId, trajectoryFilePath, fileName, thermalModulationParameters, file, "MR"));

        assertTrue(exception.getMessage().contains("Missing Areas/Cluster"));
    }
}
