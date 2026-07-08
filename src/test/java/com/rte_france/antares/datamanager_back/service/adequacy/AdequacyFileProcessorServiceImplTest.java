package com.rte_france.antares.datamanager_back.service.adequacy;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AdequacyModeRepository;
import com.rte_france.antares.datamanager_back.repository.AdequacySettingsRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import com.rte_france.antares.datamanager_back.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @InjectMocks
    private AdequacyFileProcessorServiceImpl adequacyFileProcessorService;

    @TempDir
    Path tempDir;

    private Path tempFile;
    private static final String HORIZON = "2030-2031";

    @BeforeEach
    void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(tempDir, "default.xlsx",
                List.of("perimetre", "settings"),
                List.of(
                        List.of("area", "mode"),
                        List.of("key", "value")
                ),
                List.of(
                        List.of(List.of("Area1", "mode1")),
                        List.of(List.of("include_adq_patch", true))
                )
        );

        when(trajectoryService.getTrajectoryFilePath(any(), any(), any())).thenReturn(tempFile);
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("NNI001").build());

        StudyEntity study = new StudyEntity();
        study.setId(1);
        study.setTrajectories(new LinkedHashSet<>());
        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
    }

    @Test
    void processAdequacyFile_Scenario1_SameChecksum_ThrowsException() throws IOException {
        TrajectoryEntity existing = TrajectoryEntity.builder()
                .fileName("default")
                .horizon(HORIZON)
                .type(TrajectoryType.ADEQUACY_PATCH.name())
                .checksum("SAME_CHECKSUM")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.of(existing));

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.getFileNameWithoutExtensionAndWithoutPrefix(any(), any())).thenReturn("default");
            utilsMock.when(() -> Utils.checkTrajectoryVersion(eq(tempFile), eq(existing)))
                    .thenThrow(BusinessException.builder()
                            .message("File already processes with same content {0}")
                            .errorMessageArguments(List.of("default.xlsx"))
                            .build());

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    adequacyFileProcessorService.processAdequacyFile("default.xlsx", HORIZON, 1, true)
            );

            assertTrue(exception.getMessage().contains("File already processes with same content"));
            verify(trajectoryRepository, never()).save(any());
        }
    }

    @Test
    void processAdequacyFile_Scenario2_NewTrajectory_SavesSuccessfully() throws IOException {
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());

        TrajectoryEntity savedTrajectory = new TrajectoryEntity();
        when(trajectoryRepository.save(any())).thenReturn(savedTrajectory);

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.getFileNameWithoutExtensionAndWithoutPrefix(any(), any())).thenReturn("default");
            utilsMock.when(() -> Utils.buildTrajectory(any(), anyInt(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(TrajectoryEntity.builder().scenarioEntities(new LinkedHashSet<>()).build());
            utilsMock.when(() -> Utils.getCellValue(any(), anyInt())).thenCallRealMethod();

            TrajectoryEntity result = adequacyFileProcessorService.processAdequacyFile("default.xlsx", HORIZON, 1, true);

            assertNotNull(result);
            verify(trajectoryRepository, times(1)).save(any());
            verify(adequacyModeRepository, times(1)).saveAll(any());
            verify(adequacySettingsRepository, times(1)).saveAll(any());
        }
    }

    @Test
    void processAdequacyFile_Scenario3_DifferentChecksum_IncrementsVersion() throws IOException {
        TrajectoryEntity existing = TrajectoryEntity.builder()
                .fileName("default")
                .horizon(HORIZON)
                .type(TrajectoryType.ADEQUACY_PATCH.name())
                .version(1)
                .checksum("DIFFERENT_CHECKSUM")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.of(existing));

        TrajectoryEntity savedTrajectory = new TrajectoryEntity();
        when(trajectoryRepository.save(any())).thenReturn(savedTrajectory);

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.getFileNameWithoutExtensionAndWithoutPrefix(any(), any())).thenReturn("default");
            utilsMock.when(() -> Utils.checkTrajectoryVersion(eq(tempFile), eq(existing))).thenReturn(true);

            // Should be called with version 1, so buildTrajectory will make it version 2
            utilsMock.when(() -> Utils.buildTrajectory(eq(tempFile), eq(1), eq(HORIZON), any(), eq(TrajectoryType.ADEQUACY_PATCH), any(), any(), any()))
                    .thenReturn(TrajectoryEntity.builder().scenarioEntities(new LinkedHashSet<>()).version(2).build());
            utilsMock.when(() -> Utils.getCellValue(any(), anyInt())).thenCallRealMethod();

            TrajectoryEntity result = adequacyFileProcessorService.processAdequacyFile("default.xlsx", HORIZON, 1, true);

            assertNotNull(result);
            verify(trajectoryRepository, times(1)).save(any());
        }
    }
}
