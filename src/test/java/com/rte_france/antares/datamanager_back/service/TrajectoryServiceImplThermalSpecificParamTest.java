package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
import com.rte_france.antares.datamanager_back.util.Utils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TrajectoryServiceImplThermalSpecificParamTest {

    @Mock private AreaRepository areaRepository;
    @Mock private TrajectoryRepository trajectoryRepository;
    @Mock private StudyRepository studyRepository;
    @Mock private WarningRepository warningRepository;
    @Mock private ThermalSpecificFileProcessorService thermalSpecificProcessorService;
    @Mock private AntaressDataManagerProperties props;
    @Mock private UserService userService;

    @InjectMocks private TrajectoryServiceImpl service;

    private static final String HORIZON = "2025-2026";

    @BeforeEach
    void setupProps() {
        when(props.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(props.getTrajectoryFilePath()).thenReturn("trajectories");
        when(props.getThermalParameterDirectory()).thenReturn("thermal_parameters");
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("TEST_NNI").build());
    }

    private Path createWorkbookWithHorizon(@TempDir Path tmp) throws IOException {
        var wb = new XSSFWorkbook();
        wb.createSheet(HORIZON);
        Path file = tmp.resolve("specific_param_FR_test.xlsx");
        try (OutputStream os = Files.newOutputStream(file)) { wb.write(os); }
        wb.close();
        return file;
    }

    private ThermalSpecificParametersEntity param(String node) {
        return ThermalSpecificParametersEntity.builder()
                .node(node)
                .comment("c")
                .build();
    }

    @Test
    void processThermalSpecificParameterTrajectory_happyPath_filtersByStudyAreas_andSaves(@TempDir Path tmp) throws Exception {
        // Given
        Path workbook = createWorkbookWithHorizon(tmp);

        TrajectoryServiceImpl spyService = spy(service);
        doReturn(workbook).when(spyService).getTrajectoryFilePath(eq(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER), anyString(), anyString());

        // Study areas only FR (so no missing area warning)
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(
                AreaEntity.builder().name("FR").build()
        ));

        // Params contain FR and an unrelated XX (should be filtered out)
        List<ThermalSpecificParametersEntity> params = List.of(param("FR"), param("XX"));
        when(thermalSpecificProcessorService.buildThermalSpecificParameterValueList(anyString(), any(Path.class), eq(HORIZON), anyString(), anyInt()))
                .thenReturn(params);

        // No existing trajectory
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        // Save returns the trajectory passed in
        when(thermalSpecificProcessorService.saveThermalSpecificTrajectory(any(TrajectoryEntity.class), anyList(), eq(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        TrajectoryEntity res = spyService.processThermalSpecificParameterTrajectory("specific_param_FR_test", HORIZON, "FR", 1);

        // Then
        assertNotNull(res);
        assertEquals(HORIZON, res.getHorizon());
        assertEquals("FR", res.getArea());
        // Ensure only FR remained after filtering
        ArgumentCaptor<List<ThermalSpecificParametersEntity>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(thermalSpecificProcessorService).saveThermalSpecificTrajectory(any(TrajectoryEntity.class), listCaptor.capture(), eq(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER));
        List<ThermalSpecificParametersEntity> savedParams = listCaptor.getValue();
        assertEquals(1, savedParams.size());
        assertEquals("FR", savedParams.get(0).getNode());
    }

    @Test
    void processThermalSpecificParameterTrajectory_emptyParams_throwsBusinessException(@TempDir Path tmp) throws Exception {
        // Given
        Path workbook = createWorkbookWithHorizon(tmp);
        TrajectoryServiceImpl spyService = spy(service);
        doReturn(workbook).when(spyService).getTrajectoryFilePath(eq(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER), anyString(), anyString());

        when(thermalSpecificProcessorService.buildThermalSpecificParameterValueList(anyString(), any(Path.class), eq(HORIZON), anyString(), anyInt()))
                .thenReturn(new ArrayList<>());

        // When / Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                spyService.processThermalSpecificParameterTrajectory("specific_param_FR_test", HORIZON, "FR", 1)
        );
        assertTrue(ex.getMessage().contains("No valid thermal specific parameter"));
    }

    @Test
    void processThermalSpecificParameterTrajectory_missingAreas_addsWarning(@TempDir Path tmp) throws Exception {
        // Given
        Path workbook = createWorkbookWithHorizon(tmp);
        TrajectoryServiceImpl spyService = spy(service);
        doReturn(workbook).when(spyService).getTrajectoryFilePath(eq(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER), anyString(), anyString());

        when(areaRepository.findAllByStudyId(5)).thenReturn(List.of(
                AreaEntity.builder().name("FR").build(),
                AreaEntity.builder().name("DE").build()
        ));
        // File only contains FR => DE is missing and should trigger warning
        when(thermalSpecificProcessorService.buildThermalSpecificParameterValueList(anyString(), any(Path.class), eq(HORIZON), anyString(), anyInt()))
                .thenReturn(List.of(param("FR")));

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        when(studyRepository.findById(5)).thenReturn(Optional.of(StudyEntity.builder().id(5).build()));
        // Return the trajectory passed
        when(thermalSpecificProcessorService.saveThermalSpecificTrajectory(any(TrajectoryEntity.class), anyList(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        TrajectoryEntity res = spyService.processThermalSpecificParameterTrajectory("specific_param_FR_test", HORIZON, "FR", 5);

        // Then
        assertNotNull(res);
        assertNotNull(res.getWarningMessages());
        assertEquals(1, res.getWarningMessages().size());
        WarningMessageEntity w = res.getWarningMessages().iterator().next();
        assertEquals(WarningCode.THERMAL_SPECIFIC_PARAM_MISSING_AREAS, w.getWarningCode());
        assertTrue(w.getWarningContent().contains("DE"));
        assertFalse(w.getIsAck());
    }

    @Test
    void processThermalSpecificParameterTrajectory_incrementsVersion_whenDifferentContent(@TempDir Path tmp) throws Exception {
        // Given
        Path workbook = createWorkbookWithHorizon(tmp);
        TrajectoryServiceImpl spyService = spy(service);
        doReturn(workbook).when(spyService).getTrajectoryFilePath(eq(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER), anyString(), anyString());

        when(areaRepository.findAllByStudyId(2)).thenReturn(List.of(AreaEntity.builder().name("FR").build()));
        when(thermalSpecificProcessorService.buildThermalSpecificParameterValueList(anyString(), any(Path.class), eq(HORIZON), anyString(), anyInt()))
                .thenReturn(List.of(param("FR")));

        // Existing trajectory with version 3
        TrajectoryEntity existing = TrajectoryEntity.builder()
                .fileName("specific_param_FR_test")
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .horizon(HORIZON)
                .area("FR")
                .version(3)
                .checksum("old")
                .lastModificationContentDate(LocalDateTime.now().minusDays(1))
                .build();

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(existing));

        when(studyRepository.findById(2)).thenReturn(Optional.of(StudyEntity.builder().id(2).build()));
        when(thermalSpecificProcessorService.saveThermalSpecificTrajectory(any(TrajectoryEntity.class), anyList(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<Utils> mocked = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            mocked.when(() -> Utils.checkTrajectoryVersion(any(Path.class), eq(existing))).thenReturn(true);

            TrajectoryEntity res = spyService.processThermalSpecificParameterTrajectory("specific_param_FR_test", HORIZON, "FR", 2);

            assertNotNull(res);
            assertEquals(4, res.getVersion()); // incremented from 3 to 4
        }
    }

    @Test
    void processThermalSpecificParameterTrajectory_throws_whenSelectedAreaNotInFile(@TempDir Path tmp) throws Exception {
        // Given
        Path workbook = createWorkbookWithHorizon(tmp);
        TrajectoryServiceImpl spyService = spy(service);
        doReturn(workbook).when(spyService).getTrajectoryFilePath(eq(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER), anyString(), anyString());

        // Study areas list (doesn't include AT to focus on file presence rule)
        when(areaRepository.findAllByStudyId(7)).thenReturn(List.of(
                AreaEntity.builder().name("FR").build(),
                AreaEntity.builder().name("DE").build()
        ));
        // File contains only FR in node column
        when(thermalSpecificProcessorService.buildThermalSpecificParameterValueList(anyString(), any(Path.class), eq(HORIZON), anyString(), anyInt()))
                .thenReturn(List.of(param("FR")));

        // No existing trajectory
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        // When / Then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> spyService.processThermalSpecificParameterTrajectory("specific_param_FR_test", HORIZON, "AT", 7));
        assertTrue(ex.getMessage().contains("Selected area AT is not present"));
    }
}
