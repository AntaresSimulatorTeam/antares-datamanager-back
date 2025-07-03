package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.WarningDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.WarningServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.MessageSource;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WarningMessageServiceImplTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private WarningRepository warningRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @InjectMocks
    private WarningServiceImpl warningService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getMessage_returnsCorrectMessage() {
        var code = "test.code";
        var expectedMessage = "Test message";
        when(messageSource.getMessage(eq(code), any(), eq(code), eq(Locale.getDefault()))).thenReturn(expectedMessage);

        var actualMessage = warningService.getMessage(code);

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void getNotFoundMessage_returnsCorrectMessage() {
        var code = "data.not.found";
        var expectedMessage = "Data not found";
        when(messageSource.getMessage(eq(code), any(), eq(code), eq(Locale.getDefault()))).thenReturn(expectedMessage);

        var actualMessage = warningService.getNotFoundMessage();

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void acknowledgeWarning_updatesIsAckToTrue_whenWarningExists() {
        var id = 1;
        var warning = new WarningMessageEntity();
        warning.setId(id);
        warning.setIsAck(false);

        when(warningRepository.findById(id)).thenReturn(Optional.of(warning));

        warningService.acknowledgeWarning(id);

        assertEquals(true, warning.getIsAck());
        verify(warningRepository).save(warning);
    }

    @Test
    void acknowledgeWarning_throwsBusinessException_whenWarningDoesNotExist() {
        var id = 1;

        when(warningRepository.findById(id)).thenReturn(Optional.empty());

        var exception = assertThrows(BusinessException.class, () -> warningService.acknowledgeWarning(id));

        assertEquals("Warning message not found with id: " + id, exception.getMessage());
        verify(warningRepository, never()).save(any());
    }

    @Test
    void getWarningsForTrajectory_shouldReturnEmptyList_whenTrajectoryNotFound() {
        // Given
        Integer trajectoryId = 1;
        Integer studyId = 1;
        when(trajectoryRepository.findAllByIdWithWarnings(List.of(trajectoryId)))
                .thenReturn(Collections.emptySet());

        // When
        List<WarningDTO> result = warningService.getWarningsForTrajectory(trajectoryId,studyId);

        // Then
        assertTrue(result.isEmpty());
        verify(trajectoryRepository).findAllByIdWithWarnings(List.of(trajectoryId));
    }

    @Test
    void getWarningsForTrajectory_shouldReturnEmptyList_whenNoWarnings() {
        // Given
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .warningMessages(null)
                .build();

        when(trajectoryRepository.findAllByIdWithWarnings(List.of(trajectoryId)))
                .thenReturn(Set.of(trajectory));

        // When
        List<WarningDTO> result = warningService.getWarningsForTrajectory(trajectoryId, studyId);

        // Then
        assertTrue(result.isEmpty());
        verify(trajectoryRepository).findAllByIdWithWarnings(List.of(trajectoryId));
    }

    @Test
    void getWarningsForTrajectory_shouldReturnWarnings_whenWarningsExist() {
        // Given
        Integer trajectoryId = 1;
        Integer studyId = 1;
        StudyEntity study = StudyEntity.builder().id(studyId).build();
        WarningMessageEntity warning = WarningMessageEntity.builder()
                .id(1)
                .warningContent("Test warning")
                .warningLevel(WarningLevel.ERROR_LEVEL)
                .warningCode(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS)
                .creationDate(LocalDateTime.now())
                .createdBy("testUser")
                .study(study)
                .isAck(false)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .warningMessages(Set.of(warning))
                .build();

        when(trajectoryRepository.findAllByIdWithWarnings(List.of(trajectoryId)))
                .thenReturn(Set.of(trajectory));

        // When
        List<WarningDTO> result = warningService.getWarningsForTrajectory(trajectoryId,studyId);

        // Then
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());

        WarningDTO warningDTO = result.get(0);
        assertEquals(warning.getId(), warningDTO.getId());
        assertEquals(warning.getWarningContent(), warningDTO.getContent());
        assertEquals(warning.getWarningLevel().name(), warningDTO.getLevel());
        assertEquals(warning.getWarningCode().name(), warningDTO.getCode());
        assertEquals(warning.getCreatedBy(), warningDTO.getGeneratedBy());
        assertEquals(warning.getIsAck(), warningDTO.getIsAck());

        verify(trajectoryRepository).findAllByIdWithWarnings(List.of(trajectoryId));
    }

}