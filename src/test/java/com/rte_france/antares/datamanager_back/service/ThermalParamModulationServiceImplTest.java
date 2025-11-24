package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalSpecificFileProcessorService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalParamModulationServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
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
    void processThermalModulationParameterFile_shouldSaveNewTrajectory_whenValidHorizonAndNoExistingTrajectory() throws IOException {
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


}
