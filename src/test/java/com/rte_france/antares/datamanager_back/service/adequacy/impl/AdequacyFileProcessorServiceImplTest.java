package com.rte_france.antares.datamanager_back.service.adequacy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdequacyFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;
    @Mock
    private TrajectoryServiceImpl trajectoryService;
    @Mock
    private UserService userService;
    @Mock
    private AdequacyModeRepository adequacyModeRepository;
    @Mock
    private AdequacySettingsRepository adequacySettingsRepository;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private AreaRepository areaRepository;

    @InjectMocks
    private AdequacyFileProcessorServiceImpl adequacyFileProcessorService;

    @TempDir
    Path tempDir;

    private Path excelFilePath;
    private final String trajectoryToUse = "test_adequacy";
    private final String horizon = "2023-2024";
    private final Integer studyId = 1;

    @BeforeEach
    void setUp() throws IOException {
        excelFilePath = tempDir.resolve("ADEQUACY_PATCH_test_adequacy.xlsx");
        // Create the parent directory if necessary (though tempDir should exist)
        Files.createDirectories(excelFilePath.getParent());

        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fileOut = new FileOutputStream(excelFilePath.toFile())) {
            Sheet sheetPerimetre = workbook.createSheet("perimetre");
            Row headerPerimetre = sheetPerimetre.createRow(0);
            headerPerimetre.createCell(0).setCellValue("Area");
            headerPerimetre.createCell(1).setCellValue("Mode");
            Row rowPerimetre = sheetPerimetre.createRow(1);
            rowPerimetre.createCell(0).setCellValue("FR");
            rowPerimetre.createCell(1).setCellValue("Adequacy");

            Sheet sheetSettings = workbook.createSheet("settings");
            Row rowSetting1 = sheetSettings.createRow(0);
            rowSetting1.createCell(0).setCellValue("include-adq-patch");
            rowSetting1.createCell(1).setCellValue(true);
            Row rowSetting2 = sheetSettings.createRow(1);
            rowSetting2.createCell(0).setCellValue("price-taking-order");
            rowSetting2.createCell(1).setCellValue("DENS");

            workbook.write(fileOut);
        }

        // default study areas
        when(areaRepository.findAllByStudyId(anyInt()))
                .thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                    setName("FR");
                }}));
    }

    @Test
    void processAdequacyFile_shouldSuccessfullyProcessFile() throws IOException {
        // Given
        when(trajectoryService.getTrajectoryFilePath(eq(TrajectoryType.ADEQUACY_PATCH), eq(trajectoryToUse), any())).thenReturn(excelFilePath);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("test_user").build());

        StudyEntity study = new StudyEntity();
        study.setId(studyId);
        study.setTrajectories(new java.util.HashSet<>());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TrajectoryEntity result = adequacyFileProcessorService.processAdequacyFile(trajectoryToUse, horizon, studyId, true);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFileName()).isEqualTo("ADEQUACY_PATCH_test_adequacy");
        assertThat(result.getHorizon()).isEqualTo(horizon);
        assertThat(result.getCreatedBy()).isEqualTo("test_user");
        assertThat(result.getAdequacyModeEntities()).hasSize(1);
        assertThat(result.getAdequacyModeEntities().get(0).getArea()).isEqualTo("FR");
        assertThat(result.getAdequacyModeEntities().get(0).getMode()).isEqualTo("Adequacy");

        assertThat(result.getAdequacySettingsEntities()).hasSize(1);
        assertThat(result.getAdequacySettingsEntities().get(0).getIncludeAdqPatch()).isTrue();
        assertThat(result.getAdequacySettingsEntities().get(0).getPriceTakingOrder()).isEqualTo("DENS");

        verify(adequacyModeRepository).saveAll(anyList());
        verify(adequacySettingsRepository).saveAll(anyList());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    void processAdequacyFile_shouldThrowException_whenStudyNotFound() throws IOException {
        // Given
        when(trajectoryService.getTrajectoryFilePath(any(), any(), any())).thenReturn(excelFilePath);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(studyRepository.findById(studyId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adequacyFileProcessorService.processAdequacyFile(trajectoryToUse, horizon, studyId, true))
                .isInstanceOf(TechnicalException.class)
                .hasMessageContaining("Study not found with id " + studyId);
    }

    @Test
    void processAdequacyFile_shouldHandleMissingSheets() throws IOException {
        // Given
        Path emptyExcelPath = tempDir.resolve("ADEQUACY_PATCH_empty.xlsx");
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fileOut = new FileOutputStream(emptyExcelPath.toFile())) {
            workbook.write(fileOut);
        }

        when(trajectoryService.getTrajectoryFilePath(any(), any(), any())).thenReturn(emptyExcelPath);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        StudyEntity study = new StudyEntity();
        study.setTrajectories(new java.util.HashSet<>());

        // When
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> adequacyFileProcessorService.processAdequacyFile("empty", horizon, studyId, true)
        );

        assertTrue(ex.getMessage().contains("Missing tab {0} in AdequacyPatch trajectory {1}"));
    }
}
