package com.rte_france.antares.datamanager_back.service.scenario_builder;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ScenarioBuilderRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ScenarioBuilderEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.scenario_builder.impl.ScenarioBuilderFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.Utils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.SCENARIO_BUILDER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScenarioBuilderFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private ScenarioBuilderRepository scenarioBuilderRepository;

    @Mock
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private UserService userService;

    @InjectMocks
    private ScenarioBuilderFileProcessorServiceImpl scenarioBuilderFileProcessorService;

    @TempDir
    Path tempDir;

    private Path sampleFilePath;

    @BeforeEach
    void setUp() throws IOException {
        Path baseDir = tempDir.resolve("nas");
        Path scenarioBuilderDir = baseDir.resolve("trajectories").resolve("settings/scenario_builder");
        Files.createDirectories(scenarioBuilderDir);

        lenient().when(antaresDataManagerProperties.getNasDirectory()).thenReturn(baseDir.toString());
        lenient().when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        lenient().when(antaresDataManagerProperties.getScenarioBuilderDirectory()).thenReturn("settings/scenario_builder");

        sampleFilePath = scenarioBuilderDir.resolve("scenario_builder_BP23_A_ref_vdef.xlsx");
        createSampleExcelFile(sampleFilePath);
    }

    private void createSampleExcelFile(Path path) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(path.toFile())) {
            Sheet sheet = workbook.createSheet("Sheet1");
            String[] rows = {
                    "[Default Rules]",
                    "l,fr,1 = 57",
                    "l,fr,2 = 57@*",
                    "[Hydro]",
                    "h,fr,1 = 1*",
                    "[Thermal]",
                    "@t,fr,1 = 1"
            };
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue(rows[i]);
            }
            workbook.write(fos);
        }
    }

    @Test
    void processScenarioBuilderFile_scenario2_newTrajectory_shouldInsertWithVersion1() throws IOException {
        // Given
        UserInfoDto userInfo = new UserInfoDto();
        userInfo.setNni("test_user");
        when(userService.getCurrentUserDetails()).thenReturn(userInfo);

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), eq(SCENARIO_BUILDER.name())))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(invocation -> {
            TrajectoryEntity t = invocation.getArgument(0);
            t.setId(100);
            return t;
        });

        // When
        TrajectoryEntity result = scenarioBuilderFileProcessorService.processScenarioBuilderFile(
                "scenario_builder_BP23_A_ref_vdef", "2023-2024", 1);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getVersion());
        assertEquals("BP23_A_ref_vdef", result.getFileName());
        assertEquals("SCENARIO_BUILDER", result.getType());
        assertEquals("2023-2024", result.getHorizon());
        assertEquals("test_user", result.getCreatedBy());

        // Verify entities inserted into scenario_builder table
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScenarioBuilderEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(scenarioBuilderRepository).saveAll(captor.capture());

        List<ScenarioBuilderEntity> savedEntities = captor.getValue();
        assertEquals(4, savedEntities.size());
        assertEquals("l,fr,1 = 57", savedEntities.get(0).getModulo());
        assertEquals("l,fr,2 = 57", savedEntities.get(1).getModulo());
        assertEquals("h,fr,1 = 1", savedEntities.get(2).getModulo());
        assertEquals("t,fr,1 = 1", savedEntities.get(3).getModulo());
        // Verify no headers with [ ] were saved
        assertTrue(savedEntities.stream().noneMatch(e -> e.getModulo().contains("[") || e.getModulo().contains("]")));

        // Verify study link
        verify(trajectoryService).linkTrajectoryToStudy(100, 1, SCENARIO_BUILDER);
    }

    @Test
    void processScenarioBuilderFile_scenario1_sameChecksum_shouldThrowBusinessException() throws IOException {
        // Given
        String checksum = Utils.getFileChecksum(sampleFilePath.toString());

        TrajectoryEntity existing = TrajectoryEntity.builder()
                .id(50)
                .fileName("BP23_A_ref_vdef")
                .version(1)
                .checksum(checksum)
                .horizon("2023-2024")
                .type(SCENARIO_BUILDER.name())
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                "BP23_A_ref_vdef", "2023-2024", SCENARIO_BUILDER.name()))
                .thenReturn(Optional.of(existing));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () ->
                scenarioBuilderFileProcessorService.processScenarioBuilderFile(
                        "scenario_builder_BP23_A_ref_vdef", "2023-2024", null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("File already processed with same content {0}", exception.getMessage());
        assertTrue(exception.getErrorMessageArguments().contains("scenario_builder_BP23_A_ref_vdef.xlsx"));

        // Verify no DB inserts
        verify(trajectoryRepository, never()).save(any());
        verify(scenarioBuilderRepository, never()).saveAll(any());
    }

    @Test
    void processScenarioBuilderFile_scenario3_differentChecksum_shouldInsertWithNextVersion() throws IOException {
        // Given
        TrajectoryEntity existing = TrajectoryEntity.builder()
                .id(50)
                .fileName("BP23_A_ref_vdef")
                .version(2)
                .checksum("mock_checksum_old")
                .horizon("2023-2024")
                .type(SCENARIO_BUILDER.name())
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                "BP23_A_ref_vdef", "2023-2024", SCENARIO_BUILDER.name()))
                .thenReturn(Optional.of(existing));

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(invocation -> {
            TrajectoryEntity t = invocation.getArgument(0);
            t.setId(101);
            return t;
        });

        // When
        TrajectoryEntity result = scenarioBuilderFileProcessorService.processScenarioBuilderFile(
                "scenario_builder_BP23_A_ref_vdef.xlsx", "2023-2024", null);

        // Then
        assertNotNull(result);
        assertEquals(3, result.getVersion());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        verify(scenarioBuilderRepository).saveAll(anyList());
    }

    @Test
    void processScenarioBuilderFile_folderNotFound_shouldThrowBusinessException() {
        when(antaresDataManagerProperties.getScenarioBuilderDirectory()).thenReturn("non_existent_folder");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                scenarioBuilderFileProcessorService.processScenarioBuilderFile(
                        "scenario_builder_BP23_A_ref_vdef", "2023-2024", null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("folder not found"));
    }

    @Test
    void processScenarioBuilderFile_fileNotFound_shouldThrowBusinessException() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                scenarioBuilderFileProcessorService.processScenarioBuilderFile(
                        "no_file", "2023-2024", null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("file not found"));
    }
}
