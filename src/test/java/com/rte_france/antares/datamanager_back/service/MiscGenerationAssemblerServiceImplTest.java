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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        miscGenerationAssemblerService = new MiscGenerationAssemblerServiceImpl(
                miscFileProcessorService,
                nasFileService,
                antaresDataManagerProperties,
                timeSeriesReader
        );
    }

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
        assertEquals("biogas", result.get("FR").get(0).getGroupe());
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
        assertTrue(results.get(0).toString().contains("AT.UUID.arrow"));
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
        assertEquals("FR", savedMatrix.columns().get(0).name());
        assertEquals(59.0 / 21.0, savedMatrix.columns().get(0).values()[0], 1e-9);
        assertEquals(80.0 / 21.0, savedMatrix.columns().get(0).values()[1], 1e-9);

        assertTrue(result.containsKey("FR"));
        assertEquals(3, result.get("FR").size());
        result.get("FR").forEach(dto -> {
            assertEquals(MiscGroupEnum.OTHER.value(), dto.getGroupe());
            assertEquals(List.of("FR_other.UUID.arrow"), dto.getMiscGenTsList());
        });
    }
}
