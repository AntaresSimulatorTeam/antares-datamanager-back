package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.WarningDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.StudyTrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.WarningServiceImpl;
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

    @Mock
    private StudyRepository studyRepository;

    @Mock
    private StudyTrajectoryRepository studyTrajectoryRepository;

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
        when(warningRepository.findByTrajectoryTypeAndStudyId(any(), any()))
                .thenReturn(Collections.emptySet());

        // When
        Set<WarningDTO> result = warningService.getWarningsForTrajectory(studyId, TrajectoryType.AREA);

        // Then
        assertTrue(result.isEmpty());
        verify(warningRepository).findByTrajectoryTypeAndStudyId(studyId, TrajectoryType.AREA.name());
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

        when(warningRepository.findByTrajectoryTypeAndStudyId(any(), any()))
                .thenReturn(Collections.emptySet());

        // When
        Set<WarningDTO> result = warningService.getWarningsForTrajectory(studyId, TrajectoryType.AREA);

        // Then
        assertTrue(result.isEmpty());
        verify(warningRepository).findByTrajectoryTypeAndStudyId(studyId, TrajectoryType.AREA.name());
    }

    @Test
    void getWarningsForTrajectory_shouldReturnWarnings_whenWarningsExist() {
        // Given
        Integer studyId = 1;
        StudyEntity study = StudyEntity.builder().id(studyId).build();
        WarningMessageEntity warning = WarningMessageEntity.builder()
                .id(1)
                .warningContent("Test warning")
                .warningLevel(WarningLevel.ERROR_LEVEL)
                .warningCode(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS)
                .creationDate(LocalDateTime.now())
                .createdBy("testUser")
                .trajectory(TrajectoryEntity.builder().id(1).type(TrajectoryType.AREA.name()).build())
                .study(study)
                .isAck(false)
                .build();

        when(warningRepository.findByTrajectoryTypeAndStudyId(studyId, TrajectoryType.AREA.name()))
                .thenReturn(Set.of(warning));
        when(studyTrajectoryRepository.existsById(any())).thenReturn(true);

        // When
        Set<WarningDTO> result = warningService.getWarningsForTrajectory(studyId, TrajectoryType.AREA);

        // Then
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());

        WarningDTO warningDTO = result.iterator().next();
        assertEquals(warning.getId(), warningDTO.getId());
        assertEquals(warning.getWarningContent(), warningDTO.getContent());
        assertEquals(warning.getWarningLevel().name(), warningDTO.getLevel());
        assertEquals(warning.getWarningCode().name(), warningDTO.getCode());
        assertEquals(warning.getCreatedBy(), warningDTO.getGeneratedBy());
        assertEquals(warning.getIsAck(), warningDTO.getIsAck());

        verify(warningRepository).findByTrajectoryTypeAndStudyId(studyId, TrajectoryType.AREA.name());
    }

    @Test
    void getWarningsForTrajectory_returnsSortedWarningsByAckAndCreationDate() {
        Integer studyId = 1;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .type(TrajectoryType.AREA.name())
                .build();
        StudyEntity study = StudyEntity.builder().id(studyId).build();
        WarningMessageEntity warning1 = WarningMessageEntity.builder()
                .id(1)
                .isAck(false)
                .trajectory(trajectory)
                .study(study)
                .creationDate(LocalDateTime.now().minusDays(1))
                .warningLevel(WarningLevel.ERROR_LEVEL)
                .warningCode(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS)
                .build();
        WarningMessageEntity warning2 = WarningMessageEntity.builder()
                .id(2)
                .isAck(false)
                .trajectory(trajectory)
                .study(study)
                .creationDate(LocalDateTime.now())
                .warningLevel(WarningLevel.ERROR_LEVEL)
                .warningCode(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS)
                .build();
        WarningMessageEntity warning3 = WarningMessageEntity.builder()
                .id(3)
                .isAck(true)
                .trajectory(trajectory)
                .study(study)
                .creationDate(LocalDateTime.now().minusDays(2))
                .warningLevel(WarningLevel.ERROR_LEVEL)
                .warningCode(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS)
                .build();
        WarningMessageEntity warning4 = WarningMessageEntity.builder()
                .id(4)
                .isAck(true)
                .trajectory(trajectory)
                .study(study)
                .creationDate(LocalDateTime.now().minusDays(1))
                .warningLevel(WarningLevel.ERROR_LEVEL)
                .warningCode(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS)
                .build();

        when(warningRepository.findByTrajectoryTypeAndStudyId(studyId, TrajectoryType.AREA.name()))
                .thenReturn(Set.of(warning1, warning2, warning3, warning4));
        when(studyTrajectoryRepository.existsById(any())).thenReturn(true);

        Set<WarningDTO> result = warningService.getWarningsForTrajectory(studyId, TrajectoryType.AREA);

        assertEquals(4, result.size());
        Iterator<WarningDTO> iterator = result.iterator();
        assertEquals(warning2.getId(), iterator.next().getId());
        assertEquals(warning1.getId(), iterator.next().getId());
        assertEquals(warning4.getId(), iterator.next().getId());
        assertEquals(warning3.getId(), iterator.next().getId());
    }

    @Test
    void getWarningsForTrajectory_returnsEmptySet_whenNoWarningsMatchCriteria() {
        Integer studyId = 1;

        when(warningRepository.findByTrajectoryTypeAndStudyId(studyId, TrajectoryType.AREA.name()))
                .thenReturn(Set.of());
        when(studyTrajectoryRepository.existsById(any())).thenReturn(false);

        Set<WarningDTO> result = warningService.getWarningsForTrajectory(studyId, TrajectoryType.AREA);

        assertTrue(result.isEmpty());
        verify(warningRepository).findByTrajectoryTypeAndStudyId(studyId, TrajectoryType.AREA.name());
    }

    @Test
    void getWarningsForTrajectory_returnsWarningOnlyIfBothTrajectoriesLinked() {
        Integer studyId = 1;
        StudyEntity study = StudyEntity.builder().id(studyId).build();
        TrajectoryEntity trajectory1 = TrajectoryEntity.builder().id(1).type(TrajectoryType.AREA.name()).build();
        TrajectoryEntity trajectory2 = TrajectoryEntity.builder().id(2).type(TrajectoryType.AREA.name()).build();

        WarningMessageEntity warning = WarningMessageEntity.builder()
                .id(1)
                .isAck(false)
                .trajectory(trajectory1)
                .secondTrajectory(trajectory2)
                .study(study)
                .creationDate(LocalDateTime.now())
                .warningLevel(WarningLevel.ERROR_LEVEL)
                .warningCode(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS)
                .build();

        when(warningRepository.findByTrajectoryTypeAndStudyId(studyId, TrajectoryType.AREA.name()))
                .thenReturn(Set.of(warning));
        when(studyTrajectoryRepository.existsById(
                StudyTrajectoryKey.builder().trajectoryId(1).scenarioId(studyId).build()))
                .thenReturn(true);
        when(studyTrajectoryRepository.existsById(
                StudyTrajectoryKey.builder().trajectoryId(2).scenarioId(studyId).build()))
                .thenReturn(true);

        Set<WarningDTO> result = warningService.getWarningsForTrajectory(studyId, TrajectoryType.AREA);

        assertEquals(1, result.size());
        assertEquals(warning.getId(), result.iterator().next().getId());
    }

    @Test
    void addWarning_returns_whenWarningsIsEmpty() {
        var warningMessages = new HashSet<WarningMessageEntity>();
        warningService.addWarning(warningMessages, List.of(), WarningCode.LINKS_ALL_VALUES_ZERO, 1, "test", new TrajectoryEntity());
        assertTrue(warningMessages.isEmpty());
    }

    @Test
    void addWarning_addsWarning_whenSizeIsOne() {
        var warningMessages = new HashSet<WarningMessageEntity>();
        var study = StudyEntity.builder()
                .id(1)
                .build();
        var trajectory = TrajectoryEntity.builder()
                .id(2)
                .build();
        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(warningRepository.existsByWarningContentAndTrajectoryIdAndStudyId(any(), eq(2), eq(1))).thenReturn(false);
        when(messageSource.getMessage(eq(WarningCode.LINKS_ALL_VALUES_ZERO.value()), any(), any(), any()))
                .thenReturn("warning message");

        warningService.addWarning(warningMessages, List.of("1"), WarningCode.LINKS_ALL_VALUES_ZERO, 1, "test", trajectory);

        assertEquals(1, warningMessages.size());
    }

    @Test
    void addWarning_addsWarning_whenMultipleWarningsAndCode() {
        var warningMessages = new HashSet<WarningMessageEntity>();
        var study = StudyEntity.builder().id(1).build();
        var trajectory = TrajectoryEntity.builder()
                .id(2)
                .build();
        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(warningRepository.existsByWarningContentAndTrajectoryIdAndStudyId(any(), eq(2), eq(1)))
                .thenReturn(false);
        when(messageSource.getMessage(eq(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS.value()), any(), any(), any()))
                .thenReturn("warning message");

        warningService.addWarning(warningMessages, List.of("1", "2"), WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS, 1, "test", trajectory);

        assertEquals(1, warningMessages.size());
    }

    @Test
    void addWarning_doesNotAddWarning_whenAlraedyExists() {
        var warningMessages = new HashSet<WarningMessageEntity>();
        var study = StudyEntity.builder()
                .id(1)
                .build();
        var trajectory = TrajectoryEntity.builder()
                .id(2)
                .build();
        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(warningRepository.existsByWarningContentAndTrajectoryIdAndStudyId(any(), eq(2), eq(1)))
                .thenReturn(true);
        when(messageSource.getMessage(eq(WarningCode.LINKS_ALL_VALUES_ZERO.value()), any(), any(), any()))
                .thenReturn("warning message");

        warningService.addWarning(warningMessages, List.of("A"), WarningCode.LINKS_ALL_VALUES_ZERO, 1, "test", trajectory);

        assertTrue(warningMessages.isEmpty());
    }

    @Test
    void getMessage_throwsIllegalState_whenTemplateIsNull() {
        when(messageSource.getMessage(any(), any(), any(), any())).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> warningService.getMessage("testMessage"));
    }
}