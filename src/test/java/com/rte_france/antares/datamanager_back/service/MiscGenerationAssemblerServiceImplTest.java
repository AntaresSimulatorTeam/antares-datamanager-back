package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.MiscGroupEnum;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.misc.impl.MiscFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.misc.impl.MiscGenerationAssemblerServiceImpl;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiscGenerationAssemblerServiceImplTest {

    @Mock
    private NasFileService nasFileService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private TimeSeriesReader timeSeriesReader;

    @Mock
    private MiscFileProcessorServiceImpl miscFileProcessorService;

    @InjectMocks
    private MiscGenerationAssemblerServiceImpl miscGenerationAssemblerService;

    @TempDir
    Path tempDir;

    @Test
    void assembleMiscProperties_shouldReturnCorrectMap() {
        // Given
        StudyEntity study = new StudyEntity();
        study.setId(1);
        study.setHorizon("2030-2031");

        TrajectoryEntity t1 = new TrajectoryEntity();
        t1.setType("MISC_CAPACITY");
        t1.setHorizon("2030-2031");

        MiscClusterCapacityEntity m1 = new MiscClusterCapacityEntity();
        m1.setArea("FR");
        m1.setGroupe("biogas");
        m1.setCapacityByYear(BigDecimal.valueOf(100.0));
        t1.setMiscClusterCapacityEntities(List.of(m1));
        study.setTrajectories(Set.of(t1));

        // When
        var result = miscGenerationAssemblerService.assembleMiscProperties(study);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR"));
        assertEquals(1, result.get("FR").size());
        assertEquals("biogas", result.get("FR").getFirst().getGroupe());
    }

    @Test
    void splitMiscGenLoadFiles_shouldIncludeGroupNameInFileName() throws IOException {
        // Given
        Path tempFile = tempDir.resolve("load_factor_wave_2030-2031.csv");
        Files.createFile(tempFile);
        Set<String> areas = Set.of("AT", "BE");
        String horizon = "2030-2031";
        String groupName = "wave";
        String outputDir = "misc_gen_ts";

        TimeSeriesMatrixColumn colAT = new TimeSeriesMatrixColumn("AT", new double[]{0.1, 0.2});
        TimeSeriesMatrixColumn colBE = new TimeSeriesMatrixColumn("BE", new double[]{0.3, 0.4});
        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(colAT, colBE));

        when(timeSeriesReader.readFromTxt(tempFile)).thenReturn(matrix);
        when(antaresDataManagerProperties.getMiscGenTsOutputDirectory()).thenReturn(outputDir);
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("AT_wave"), eq(outputDir))).thenReturn("AT_wave.UUID.arrow");
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("BE_wave"), eq(outputDir))).thenReturn("BE_wave.UUID.arrow");

        // When
        List<Path> results = miscGenerationAssemblerService.splitMiscGenLoadFiles(tempFile, areas, horizon, groupName);

        // Then
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(p -> p.toString().contains("AT_wave.UUID.arrow")));
        assertTrue(results.stream().anyMatch(p -> p.toString().contains("BE_wave.UUID.arrow")));
    }

    @Test
    void splitMiscGenLoadFiles_shouldNotIncludeGroupNameIfEmpty() throws IOException {
        // Given
        Path tempFile = tempDir.resolve("load_factor_2030-2031.csv");
        Files.createFile(tempFile);
        Set<String> areas = Set.of("AT");
        String horizon = "2030-2031";
        String groupName = "";
        String outputDir = "misc_gen_ts";

        TimeSeriesMatrixColumn colAT = new TimeSeriesMatrixColumn("AT", new double[]{0.1, 0.2});
        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(colAT));

        when(timeSeriesReader.readFromTxt(tempFile)).thenReturn(matrix);
        when(antaresDataManagerProperties.getMiscGenTsOutputDirectory()).thenReturn(outputDir);
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("AT"), eq(outputDir))).thenReturn("AT.UUID.arrow");

        // When
        List<Path> results = miscGenerationAssemblerService.splitMiscGenLoadFiles(tempFile, areas, horizon, groupName);

        // Then
        assertEquals(1, results.size());
        assertTrue(results.getFirst().toString().contains("AT.UUID.arrow"));
    }

    @Test
    void assembleMiscProperties_shouldAggregateOtherAliasesBeforeWritingArrowFile() throws IOException {
        StudyEntity study = new StudyEntity();
        study.setId(1);
        study.setHorizon("2030-2031");

        TrajectoryEntity capacityTrajectory = new TrajectoryEntity();
        capacityTrajectory.setId(10);
        capacityTrajectory.setType("MISC_CAPACITY");
        capacityTrajectory.setArea("OTHERS");
        capacityTrajectory.setHorizon("2030-2031");

        MiscClusterCapacityEntity other = new MiscClusterCapacityEntity();
        other.setArea("FR");
        other.setGroupe("other");
        other.setCluster("cluster_other");
        other.setCapacityByYear(BigDecimal.valueOf(5.0));

        MiscClusterCapacityEntity wave = new MiscClusterCapacityEntity();
        wave.setArea("FR");
        wave.setGroupe("wave");
        wave.setCluster("cluster_wave");
        wave.setCapacityByYear(BigDecimal.valueOf(7.0));

        MiscClusterCapacityEntity hydro = new MiscClusterCapacityEntity();
        hydro.setArea("FR");
        hydro.setGroupe("hydrokinetic");
        hydro.setCluster("cluster_hydro");
        hydro.setCapacityByYear(BigDecimal.valueOf(9.0));
        capacityTrajectory.setMiscClusterCapacityEntities(List.of(other, wave, hydro));

        TrajectoryEntity loadTrajectory = new TrajectoryEntity();
        loadTrajectory.setType("MISC_LOAD");
        loadTrajectory.setArea("OTHERS");
        loadTrajectory.setFileName("misc_load_case");

        study.setTrajectories(Set.of(capacityTrajectory, loadTrajectory));

        String trajectoryRoot = "traj";
        String miscLoadDir = "misc_load";
        String outputDir = "misc_gen_ts";
        Path basePath = tempDir.resolve(trajectoryRoot).resolve(miscLoadDir).resolve("misc_load_case");

        Path waveFile = basePath.resolve("wave").resolve("cluster_wave").resolve("load_factor_cluster_wave_2030-2031.csv");
        Path hydroFile = basePath.resolve("hydrokinetic").resolve("cluster_hydro").resolve("load_factor_cluster_hydro_2030-2031.csv");
        Path otherFile = basePath.resolve("other").resolve("cluster_other").resolve("load_factor_cluster_other_2030-2031.csv");
        Files.createDirectories(waveFile.getParent());
        Files.createDirectories(hydroFile.getParent());
        Files.createDirectories(otherFile.getParent());
        Files.createFile(waveFile);
        Files.createFile(hydroFile);
        Files.createFile(otherFile);

        Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> groupMap = new LinkedHashMap<>();
        groupMap.put(new MiscFileProcessorServiceImpl.GroupClusterKey("wave", "cluster_wave"), List.of("FR"));
        groupMap.put(new MiscFileProcessorServiceImpl.GroupClusterKey("hydrokinetic", "cluster_hydro"), List.of("FR"));
        groupMap.put(new MiscFileProcessorServiceImpl.GroupClusterKey("other", "cluster_other"), List.of("FR"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryRoot);
        when(antaresDataManagerProperties.getMiscLoadDirectory()).thenReturn(miscLoadDir);
        when(antaresDataManagerProperties.getMiscGenTsOutputDirectory()).thenReturn(outputDir);
        when(miscFileProcessorService.getAreasByGroupClusterByTrajectoryId(10)).thenReturn(groupMap);
        when(timeSeriesReader.readFromTxt(waveFile)).thenReturn(new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn("FR", new double[]{1.0, 2.0}))));
        when(timeSeriesReader.readFromTxt(hydroFile)).thenReturn(new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn("FR", new double[]{3.0, 4.0}))));
        when(timeSeriesReader.readFromTxt(otherFile)).thenReturn(new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn("FR", new double[]{5.0, 6.0}))));
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("FR_other"), eq(outputDir))).thenReturn("FR_other.UUID.arrow");

        Map<String, List<com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO>> result = miscGenerationAssemblerService.assembleMiscProperties(study);

        ArgumentCaptor<TimeSeriesMatrix> matrixCaptor = ArgumentCaptor.forClass(TimeSeriesMatrix.class);
        verify(nasFileService, times(1)).saveMatrixToNas(matrixCaptor.capture(), eq("FR_other"), eq(outputDir));

        TimeSeriesMatrix savedMatrix = matrixCaptor.getValue();
        assertEquals(1, savedMatrix.columns().size());
        assertEquals("FR", savedMatrix.columns().getFirst().name());
        assertEquals(59.0 / 21.0, savedMatrix.columns().getFirst().values()[0], 1e-9);
        assertEquals(80.0 / 21.0, savedMatrix.columns().getFirst().values()[1], 1e-9);

        assertTrue(result.containsKey("FR"));
        assertEquals(3, result.get("FR").size());
        result.get("FR").forEach(dto -> {
            assertEquals(MiscGroupEnum.OTHER.value(), dto.getGroupe());
            assertEquals(List.of("FR_other.UUID.arrow"), dto.getMiscGenTsList());
        });
    }

    @Test
    void assembleMiscProperties_CapacityOtherAndLoadSpecificArea_shouldAggregateOtherAliasesBeforeWritingArrowFile() throws IOException {
        StudyEntity study = new StudyEntity();
        study.setId(1);
        study.setHorizon("2030-2031");

        TrajectoryEntity capacityTrajectory = new TrajectoryEntity();
        capacityTrajectory.setId(10);
        capacityTrajectory.setType("MISC_CAPACITY");
        capacityTrajectory.setArea("OTHERS");
        capacityTrajectory.setHorizon("2030-2031");

        MiscClusterCapacityEntity other = new MiscClusterCapacityEntity();
        other.setArea("FR");
        other.setGroupe("other");
        other.setCluster("cluster_other");
        other.setCapacityByYear(BigDecimal.valueOf(5.0));
        MiscClusterCapacityEntity wave = new MiscClusterCapacityEntity();
        wave.setArea("FR");
        wave.setGroupe("wave");
        wave.setCluster("cluster_wave");
        wave.setCapacityByYear(BigDecimal.valueOf(7.0));
        MiscClusterCapacityEntity hydro = new MiscClusterCapacityEntity();
        hydro.setArea("FR");
        hydro.setGroupe("hydrokinetic");
        hydro.setCluster("cluster_hydro");
        hydro.setCapacityByYear(BigDecimal.valueOf(9.0));
        capacityTrajectory.setMiscClusterCapacityEntities(List.of(other, wave, hydro));

        TrajectoryEntity loadTrajectory = new TrajectoryEntity();
        loadTrajectory.setType("MISC_LOAD");
        loadTrajectory.setArea("FR");
        loadTrajectory.setFileName("misc_load_case");

        study.setTrajectories(Set.of(capacityTrajectory, loadTrajectory));

        String trajectoryRoot = "traj";
        String miscLoadDir = "misc_load";
        String outputDir = "misc_gen_ts";
        Path basePath = tempDir.resolve(trajectoryRoot).resolve(miscLoadDir).resolve("misc_load_case");

        Path waveFile = basePath.resolve("wave").resolve("cluster_wave").resolve("load_factor_cluster_wave_2030-2031.csv");
        Path hydroFile = basePath.resolve("hydrokinetic").resolve("cluster_hydro").resolve("load_factor_cluster_hydro_2030-2031.csv");
        Path otherFile = basePath.resolve("other").resolve("cluster_other").resolve("load_factor_cluster_other_2030-2031.csv");
        Files.createDirectories(waveFile.getParent());
        Files.createDirectories(hydroFile.getParent());
        Files.createDirectories(otherFile.getParent());
        Files.createFile(waveFile);
        Files.createFile(hydroFile);
        Files.createFile(otherFile);

        Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> groupMap = new LinkedHashMap<>();
        groupMap.put(new MiscFileProcessorServiceImpl.GroupClusterKey("wave", "cluster_wave"), List.of("FR"));
        groupMap.put(new MiscFileProcessorServiceImpl.GroupClusterKey("hydrokinetic", "cluster_hydro"), List.of("FR"));
        groupMap.put(new MiscFileProcessorServiceImpl.GroupClusterKey("other", "cluster_other"), List.of("FR"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryRoot);
        when(antaresDataManagerProperties.getMiscLoadDirectory()).thenReturn(miscLoadDir);
        when(antaresDataManagerProperties.getMiscGenTsOutputDirectory()).thenReturn(outputDir);
        when(miscFileProcessorService.getAreasByGroupClusterByTrajectoryId(10)).thenReturn(groupMap);
        when(timeSeriesReader.readFromTxt(waveFile)).thenReturn(new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn("FR", new double[]{1.0, 2.0}))));
        when(timeSeriesReader.readFromTxt(hydroFile)).thenReturn(new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn("FR", new double[]{3.0, 4.0}))));
        when(timeSeriesReader.readFromTxt(otherFile)).thenReturn(new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn("FR", new double[]{5.0, 6.0}))));
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("FR_other"), eq(outputDir))).thenReturn("FR_other.UUID.arrow");

        Map<String, List<com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO>> result = miscGenerationAssemblerService.assembleMiscProperties(study);

        ArgumentCaptor<TimeSeriesMatrix> matrixCaptor = ArgumentCaptor.forClass(TimeSeriesMatrix.class);
        verify(nasFileService, times(1)).saveMatrixToNas(matrixCaptor.capture(), eq("FR_other"), eq(outputDir));

        TimeSeriesMatrix savedMatrix = matrixCaptor.getValue();
        assertEquals(1, savedMatrix.columns().size());
        assertEquals("FR", savedMatrix.columns().getFirst().name());
        assertEquals(59.0 / 21.0, savedMatrix.columns().getFirst().values()[0], 1e-9);
        assertEquals(80.0 / 21.0, savedMatrix.columns().getFirst().values()[1], 1e-9);

        assertTrue(result.containsKey("FR"));
        assertEquals(3, result.get("FR").size());
        result.get("FR").forEach(dto -> {
            assertEquals(MiscGroupEnum.OTHER.value(), dto.getGroupe());
            assertEquals(List.of("FR_other.UUID.arrow"), dto.getMiscGenTsList());
        });
    }

    @Test
    void assembleMiscProperties_shouldProcessNonOtherAreaWhenLoadTrajectoryHasFileName() throws IOException {
        StudyEntity study = new StudyEntity();
        study.setId(1);
        study.setHorizon("2030-2031");

        TrajectoryEntity capacityTrajectory = new TrajectoryEntity();
        capacityTrajectory.setId(100);
        capacityTrajectory.setType("MISC_CAPACITY");
        capacityTrajectory.setArea("BE");
        capacityTrajectory.setHorizon("2030-2031");

        MiscClusterCapacityEntity hydro = new MiscClusterCapacityEntity();
        hydro.setArea("BE");
        hydro.setGroupe("hydrokinetic");
        hydro.setCluster("cluster_hydro");
        hydro.setCapacityByYear(BigDecimal.valueOf(10.0));
        capacityTrajectory.setMiscClusterCapacityEntities(List.of(hydro));

        TrajectoryEntity loadTrajectory = new TrajectoryEntity();
        loadTrajectory.setType("MISC_LOAD");
        loadTrajectory.setArea("BE");
        loadTrajectory.setFileName("misc_load_be");

        study.setTrajectories(Set.of(capacityTrajectory, loadTrajectory));

        String trajectoryRoot = "traj";
        String miscLoadDir = "misc_load";
        String outputDir = "misc_gen_ts";
        Path basePath = tempDir.resolve(trajectoryRoot).resolve(miscLoadDir).resolve("misc_load_be");
        Path hydroFile = basePath.resolve("hydrokinetic").resolve("cluster_hydro").resolve("load_factor_cluster_hydro_2030-2031.csv");
        Files.createDirectories(hydroFile.getParent());
        Files.createFile(hydroFile);

        Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> groupMap = new LinkedHashMap<>();
        groupMap.put(new MiscFileProcessorServiceImpl.GroupClusterKey("hydrokinetic", "cluster_hydro"), List.of("BE"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryRoot);
        when(antaresDataManagerProperties.getMiscLoadDirectory()).thenReturn(miscLoadDir);
        when(antaresDataManagerProperties.getMiscGenTsOutputDirectory()).thenReturn(outputDir);
        when(miscFileProcessorService.getAreasByGroupClusterByTrajectoryId(100)).thenReturn(groupMap);
        when(timeSeriesReader.readFromTxt(hydroFile)).thenReturn(new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn("BE", new double[]{0.2, 0.4}))));
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("BE_other"), eq(outputDir))).thenReturn("BE_other.UUID.arrow");

        Map<String, List<com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO>> result = miscGenerationAssemblerService.assembleMiscProperties(study);

        assertTrue(result.containsKey("BE"));
        assertEquals(List.of("BE_other.UUID.arrow"), result.get("BE").getFirst().getMiscGenTsList());
    }

    @Test
    void assembleMiscProperties_shouldFallbackToOthersLoadTrajectoryWhenSpecificAreaLoadTrajectoryIsMissing() throws IOException {
        StudyEntity study = new StudyEntity();
        study.setId(1);
        study.setHorizon("2030-2031");

        TrajectoryEntity capacityTrajectory = new TrajectoryEntity();
        capacityTrajectory.setId(101);
        capacityTrajectory.setType("MISC_CAPACITY");
        capacityTrajectory.setArea("BE");

        MiscClusterCapacityEntity hydro = new MiscClusterCapacityEntity();
        hydro.setArea("BE");
        hydro.setGroupe("hydrokinetic");
        hydro.setCluster("cluster_hydro");
        hydro.setCapacityByYear(BigDecimal.valueOf(10.0));
        capacityTrajectory.setMiscClusterCapacityEntities(List.of(hydro));

        TrajectoryEntity loadTrajectory = new TrajectoryEntity();
        loadTrajectory.setType("MISC_LOAD");
        loadTrajectory.setArea("OTHERS");
        loadTrajectory.setFileName("misc_load_others");

        study.setTrajectories(Set.of(capacityTrajectory, loadTrajectory));

        String trajectoryRoot = "traj";
        String miscLoadDir = "misc_load";
        String outputDir = "misc_gen_ts";
        Path basePath = tempDir.resolve(trajectoryRoot).resolve(miscLoadDir).resolve("misc_load_others");
        Path hydroFile = basePath.resolve("hydrokinetic").resolve("cluster_hydro").resolve("load_factor_cluster_hydro_2030-2031.csv");
        Files.createDirectories(hydroFile.getParent());
        Files.createFile(hydroFile);

        Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> groupMap = new LinkedHashMap<>();
        groupMap.put(new MiscFileProcessorServiceImpl.GroupClusterKey("hydrokinetic", "cluster_hydro"), List.of("BE"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryRoot);
        when(antaresDataManagerProperties.getMiscLoadDirectory()).thenReturn(miscLoadDir);
        when(antaresDataManagerProperties.getMiscGenTsOutputDirectory()).thenReturn(outputDir);
        when(miscFileProcessorService.getAreasByGroupClusterByTrajectoryId(101)).thenReturn(groupMap);
        when(timeSeriesReader.readFromTxt(hydroFile)).thenReturn(new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn("BE", new double[]{0.2, 0.4}))));
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("BE_other"), eq(outputDir))).thenReturn("BE_other.UUID.arrow");

        Map<String, List<com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO>> result = miscGenerationAssemblerService.assembleMiscProperties(study);

        verify(miscFileProcessorService, times(1)).getAreasByGroupClusterByTrajectoryId(101);
        assertTrue(result.containsKey("BE"));
        assertEquals(List.of("BE_other.UUID.arrow"), result.get("BE").getFirst().getMiscGenTsList());
    }

    @Test
    void assembleMiscProperties_shouldNotFallbackToOthersLoadTrajectoryForFR() throws IOException {
        StudyEntity study = new StudyEntity();
        study.setId(1);
        study.setHorizon("2030-2031");

        TrajectoryEntity capacityTrajectory = new TrajectoryEntity();
        capacityTrajectory.setId(102);
        capacityTrajectory.setType("MISC_CAPACITY");
        capacityTrajectory.setArea("FR");

        MiscClusterCapacityEntity biogas = new MiscClusterCapacityEntity();
        biogas.setArea("FR");
        biogas.setGroupe("hydrokinetic");
        biogas.setCluster("cluster_hydro");
        biogas.setCapacityByYear(BigDecimal.valueOf(10.0));
        capacityTrajectory.setMiscClusterCapacityEntities(List.of(biogas));

        TrajectoryEntity loadTrajectory = new TrajectoryEntity();
        loadTrajectory.setType("MISC_LOAD");
        loadTrajectory.setArea("OTHERS");
        loadTrajectory.setFileName("misc_load_others");

        study.setTrajectories(Set.of(capacityTrajectory, loadTrajectory));

        Map<String, List<com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO>> result = miscGenerationAssemblerService.assembleMiscProperties(study);

        verify(miscFileProcessorService, never()).getAreasByGroupClusterByTrajectoryId(anyInt());
        verify(miscFileProcessorService, never()).getAreasByGroupClusterByStudyId(anyInt(), anyString());
        verify(nasFileService, never()).saveMatrixToNas(any(TimeSeriesMatrix.class), anyString(), anyString());
        assertTrue(result.containsKey("FR"));
        assertTrue(result.get("FR").getFirst().getMiscGenTsList().isEmpty());
    }

    @Test
    void splitMiscGenLoadFiles_shouldUseXlsxReaderWithHorizon() throws IOException {
        Path xlsxFile = tempDir.resolve("load_factor_wave.xlsx");
        Files.createFile(xlsxFile);

        String outputDir = "misc_gen_ts";
        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(
                new TimeSeriesMatrixColumn("AT", new double[]{0.1, 0.2}),
                new TimeSeriesMatrixColumn("BE", new double[]{0.3, 0.4})
        ));

        when(timeSeriesReader.readFromXlsx(xlsxFile, "2030-2031")).thenReturn(matrix);
        when(antaresDataManagerProperties.getMiscGenTsOutputDirectory()).thenReturn(outputDir);
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("AT_wave"), eq(outputDir))).thenReturn("AT_wave.UUID.arrow");
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("BE_wave"), eq(outputDir))).thenReturn("BE_wave.UUID.arrow");

        List<Path> results = miscGenerationAssemblerService.splitMiscGenLoadFiles(xlsxFile, Set.of("AT", "BE"), "2030-2031", "wave");

        verify(timeSeriesReader, times(1)).readFromXlsx(xlsxFile, "2030-2031");
        assertEquals(2, results.size());
    }

    @Test
    void splitMiscGenLoadFiles_shouldReturnEmptyListForUnsupportedExtension() throws IOException {
        Path unsupported = tempDir.resolve("load_factor_wave.json");
        Files.createFile(unsupported);

        List<Path> results = miscGenerationAssemblerService.splitMiscGenLoadFiles(unsupported, Set.of("AT"), "2030-2031", "wave");

        assertTrue(results.isEmpty());
        verify(timeSeriesReader, never()).readFromTxt(any(Path.class));
        verify(timeSeriesReader, never()).readFromXlsx(any(Path.class), anyString());
        verify(nasFileService, never()).saveMatrixToNas(any(TimeSeriesMatrix.class), anyString(), anyString());
    }

    @Test
    void splitMiscGenLoadFiles_shouldWrapRuntimeReaderExceptionIntoIOException() throws IOException {
        Path csvFile = tempDir.resolve("load_factor_wave_2030-2031.csv");
        Files.createFile(csvFile);

        RuntimeException cause = new RuntimeException("boom");
        when(timeSeriesReader.readFromTxt(csvFile)).thenThrow(cause);

        IOException thrown = assertThrows(IOException.class,
                () -> miscGenerationAssemblerService.splitMiscGenLoadFiles(csvFile, Set.of("AT"), "2030-2031", "wave"));

        assertSame(cause, thrown.getCause());
    }

    @Test
    void assembleMiscProperties_shouldResolveGroupsByStudyIdWhenCapacityTrajectoryIdIsNull() throws IOException {
        StudyEntity study = new StudyEntity();
        study.setId(77);
        study.setHorizon("2030-2031");

        TrajectoryEntity capacityTrajectory = new TrajectoryEntity();
        capacityTrajectory.setType("MISC_CAPACITY");
        capacityTrajectory.setArea("FR");
        capacityTrajectory.setHorizon("2030-2031");

        MiscClusterCapacityEntity biogas = new MiscClusterCapacityEntity();
        biogas.setArea("FR");
        biogas.setGroupe("biogas");
        biogas.setCluster("cluster_biogas");
        biogas.setCapacityByYear(BigDecimal.valueOf(12.0));
        capacityTrajectory.setMiscClusterCapacityEntities(List.of(biogas));

        TrajectoryEntity loadTrajectory = new TrajectoryEntity();
        loadTrajectory.setType("MISC_LOAD");
        loadTrajectory.setArea("FR");
        loadTrajectory.setFileName("misc_load_fr");

        study.setTrajectories(Set.of(capacityTrajectory, loadTrajectory));

        String trajectoryRoot = "traj";
        String miscLoadDir = "misc_load";
        String outputDir = "misc_gen_ts";
        Path basePath = tempDir.resolve(trajectoryRoot).resolve(miscLoadDir).resolve("misc_load_fr");
        Path biogasFile = basePath.resolve("biogas").resolve("cluster_biogas").resolve("load_factor_cluster_biogas_2030-2031.csv");
        Files.createDirectories(biogasFile.getParent());
        Files.createFile(biogasFile);

        Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> groupMap = new LinkedHashMap<>();
        groupMap.put(new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "cluster_biogas"), List.of("FR"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryRoot);
        when(antaresDataManagerProperties.getMiscLoadDirectory()).thenReturn(miscLoadDir);
        when(antaresDataManagerProperties.getMiscGenTsOutputDirectory()).thenReturn(outputDir);
        when(miscFileProcessorService.getAreasByGroupClusterByStudyId(77, "FR")).thenReturn(groupMap);
        when(timeSeriesReader.readFromTxt(biogasFile)).thenReturn(new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn("FR", new double[]{0.2, 0.4}))));
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("FR_biogas"), eq(outputDir))).thenReturn("FR_biogas.UUID.arrow");

        miscGenerationAssemblerService.assembleMiscProperties(study);

        verify(miscFileProcessorService, times(1)).getAreasByGroupClusterByStudyId(77, "FR");
        verify(miscFileProcessorService, never()).getAreasByGroupClusterByTrajectoryId(anyInt());
    }

    @Test
    void splitMiscGenLoadFiles_shouldIgnoreEmptyOrNonMatchingColumns() throws IOException {
        Path csvFile = tempDir.resolve("load_factor_wave_2030-2031.csv");
        Files.createFile(csvFile);

        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(
                new TimeSeriesMatrixColumn("", new double[]{0.1, 0.2}),
                new TimeSeriesMatrixColumn("AT", new double[]{0.3, 0.4})
        ));
        when(timeSeriesReader.readFromTxt(csvFile)).thenReturn(matrix);

        List<Path> results = miscGenerationAssemblerService.splitMiscGenLoadFiles(csvFile, Set.of("BE"), "2030-2031", "wave");

        assertTrue(results.isEmpty());
        verify(nasFileService, never()).saveMatrixToNas(any(TimeSeriesMatrix.class), anyString(), anyString());
    }

    @Test
    void assembleMiscProperties_shouldNotFallbackToOthersCapacityWhenSpecificAreaCapacityExists() throws IOException {
        // Given: BE has specific capacity trajectory (but no load trajectory)
        //        OTHERS has capacity trajectory (with BE entries)
        // Expected: BE should NOT use OTHERS capacity, should be marked as processed
        StudyEntity study = new StudyEntity();
        study.setId(1);
        study.setHorizon("2030-2031");

        // Specific capacity trajectory for BE with biomass capacity 674
        TrajectoryEntity beCapacityTrajectory = new TrajectoryEntity();
        beCapacityTrajectory.setId(200);
        beCapacityTrajectory.setType("MISC_CAPACITY");
        beCapacityTrajectory.setArea("BE");
        beCapacityTrajectory.setHorizon("2030-2031");

        MiscClusterCapacityEntity beBiomass = new MiscClusterCapacityEntity();
        beBiomass.setArea("BE");
        beBiomass.setGroupe("biomass");
        beBiomass.setCluster("Small biomass");
        beBiomass.setCapacityByYear(BigDecimal.valueOf(674.0));
        beCapacityTrajectory.setMiscClusterCapacityEntities(List.of(beBiomass));

        // OTHERS capacity trajectory with BE biomass capacity 567 and other areas
        TrajectoryEntity othersCapacityTrajectory = new TrajectoryEntity();
        othersCapacityTrajectory.setId(201);
        othersCapacityTrajectory.setType("MISC_CAPACITY");
        othersCapacityTrajectory.setArea("OTHERS");
        othersCapacityTrajectory.setHorizon("2030-2031");

        MiscClusterCapacityEntity othersBEBiomass = new MiscClusterCapacityEntity();
        othersBEBiomass.setArea("BE"); // Be has entry in OTHERS trajectory
        othersBEBiomass.setGroupe("biomass");
        othersBEBiomass.setCluster("Small biomass");
        othersBEBiomass.setCapacityByYear(BigDecimal.valueOf(567.0)); // Different capacity value

        MiscClusterCapacityEntity othersDEBiomass = new MiscClusterCapacityEntity();
        othersDEBiomass.setArea("DE");
        othersDEBiomass.setGroupe("biomass");
        othersDEBiomass.setCluster("Small biomass");
        othersDEBiomass.setCapacityByYear(BigDecimal.valueOf(8800.0));

        othersCapacityTrajectory.setMiscClusterCapacityEntities(List.of(othersBEBiomass, othersDEBiomass));

        // No load trajectories (so BE won't process with specific load)
        study.setTrajectories(Set.of(beCapacityTrajectory, othersCapacityTrajectory));

        // When
        Map<String, List<com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO>> result =
                miscGenerationAssemblerService.assembleMiscProperties(study);

        // Then: Result should contain single BE entry
        assertTrue(result.containsKey("BE"));
        assertEquals(1, result.get("BE").size());
        assertEquals(674.0, result.get("BE").getFirst().getCapacity());

        // And: Result should contain single DE entry (from others)
        assertTrue(result.containsKey("DE"));
        assertEquals(1, result.get("DE").size());
        assertEquals(8800.0, result.get("DE").getFirst().getCapacity());
    }

    @Test
    void assembleMiscProperties_shouldLogAndSkipWhenFileReadingThrowsException() throws IOException {
        // Given
        StudyEntity study = new StudyEntity();
        study.setId(42);
        study.setHorizon("2030-2031");

        TrajectoryEntity capacityTrajectory = new TrajectoryEntity();
        capacityTrajectory.setId(500);
        capacityTrajectory.setType("MISC_CAPACITY");
        capacityTrajectory.setArea("BE");

        MiscClusterCapacityEntity biomass = new MiscClusterCapacityEntity();
        biomass.setArea("BE");
        biomass.setGroupe("biomass");
        biomass.setCluster("Small biomass");
        biomass.setCapacityByYear(BigDecimal.valueOf(100.0));
        capacityTrajectory.setMiscClusterCapacityEntities(List.of(biomass));

        TrajectoryEntity loadTrajectory = new TrajectoryEntity();
        loadTrajectory.setType("MISC_LOAD");
        loadTrajectory.setArea("BE");
        loadTrajectory.setFileName("misc_load_be");

        study.setTrajectories(Set.of(capacityTrajectory, loadTrajectory));

        String trajectoryRoot = "traj";
        String miscLoadDir = "misc_load";
        Path basePath = tempDir.resolve(trajectoryRoot).resolve(miscLoadDir).resolve("misc_load_be");

        // Create the physical file so Files.exists(tsFilePath) returns true
        Path badFile = basePath.resolve("biomass").resolve("Small biomass").resolve("load_factor_Small biomass_2030-2031.csv");
        Files.createDirectories(badFile.getParent());
        Files.createFile(badFile);

        Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> groupMap = new LinkedHashMap<>();
        groupMap.put(new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "Small biomass"), List.of("BE"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryRoot);
        when(antaresDataManagerProperties.getMiscLoadDirectory()).thenReturn(miscLoadDir);
        when(miscFileProcessorService.getAreasByGroupClusterByTrajectoryId(500)).thenReturn(groupMap);

        // Force a RuntimeException during the read operation
        when(timeSeriesReader.readFromTxt(badFile)).thenThrow(new RuntimeException("Simulated disk read failure"));

        // When
        Map<String, List<com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO>> result =
                miscGenerationAssemblerService.assembleMiscProperties(study);

        // Then
        assertTrue(result.containsKey("BE"));
        assertEquals(1, result.get("BE").size());
        assertTrue(result.get("BE").getFirst().getMiscGenTsList().isEmpty());
    }
}
