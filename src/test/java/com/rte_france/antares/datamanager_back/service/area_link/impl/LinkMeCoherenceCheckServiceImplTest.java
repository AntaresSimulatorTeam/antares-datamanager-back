package com.rte_france.antares.datamanager_back.service.area_link.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.LinkMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests for LinkMeCoherenceCheckService
 * Validation of LINK_ME trajectory coherence with AREA and AREA_ME trajectories
 */
@ExtendWith(MockitoExtension.class)
class LinkMeCoherenceCheckServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @InjectMocks
    private LinkMeCoherenceCheckServiceImpl linkMeCoherenceCheckService;

    private Integer studyId = 1;
    private Integer trajectoryId = 100;

    @BeforeEach
    void setUp() {
        studyId = 1;
        trajectoryId = 100;
    }

    /**
     * Test: No LINK_ME entities - validation should pass without error
     */
    @Test
    void validateLinkMeCoherence_emptyLinkMeEntities_shouldPass() {
        // Arrange
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(new ArrayList<>())
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> linkMeCoherenceCheckService.validateLinkMeCoherence(studyId, trajectory));
    }

    /**
     * Test: No AREA/AREA_ME linked to study - validation should pass without error
     */
    @Test
    void validateLinkMeCoherence_noAreaTrajectories_shouldPass() {
        // Arrange
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(List.of(LinkMeEntity.builder()
                        .nodeFrom("FR")
                        .nodeTo("DE")
                        .build()))
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId))
                .thenReturn(new ArrayList<>());
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(new ArrayList<>());

        // Act & Assert
        assertDoesNotThrow(() -> linkMeCoherenceCheckService.validateLinkMeCoherence(studyId, trajectory));
    }

    /**
     * Test: Valid nodeFrom and nodeTo - validation should pass
     */
    @Test
    void validateLinkMeCoherence_validNodeFromAndNodeTo_shouldPass() {
        // Arrange
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(List.of(LinkMeEntity.builder()
                        .nodeFrom("FR")
                        .nodeTo("DE")
                        .build()))
                .build();

        // Create AREA trajectory with FR area
        AreaEntity frArea = AreaEntity.builder().name("FR").build();
        AreaConfigEntity frConfig = new AreaConfigEntity("value1", 1.0, 2.0, frArea);
        TrajectoryEntity areaTrajectory = TrajectoryEntity.builder()
                .id(1)
                .type(TrajectoryType.AREA.name())
                .areaConfigEntities(List.of(frConfig))
                .linkMeEntities(new ArrayList<>())
                .build();

        // Create AREA_ME trajectory with DE area
        AreaEntity deArea = AreaEntity.builder().name("DE").build();
        AreaConfigEntity deConfig = new AreaConfigEntity("value1", 1.0, 2.0, deArea);
        TrajectoryEntity areaMeTrajectory = TrajectoryEntity.builder()
                .id(2)
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(deConfig))
                .linkMeEntities(new ArrayList<>())
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId))
                .thenReturn(List.of(areaTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        // Act & Assert
        assertDoesNotThrow(() -> linkMeCoherenceCheckService.validateLinkMeCoherence(studyId, trajectory));
    }

    /**
     * Test RG1: nodeFrom area not in AREA or AREA_ME - should throw BusinessException
     */
    @Test
    void validateLinkMeCoherence_nodeFromNotInAreaOrAreaMe_shouldThrowException() {
        // Arrange
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(List.of(LinkMeEntity.builder()
                        .nodeFrom("MISSING_AREA")
                        .nodeTo("DE")
                        .build()))
                .build();

        AreaEntity deArea = AreaEntity.builder().name("DE").build();
        AreaConfigEntity deConfig = new AreaConfigEntity("value1", 1.0, 2.0, deArea);
        TrajectoryEntity areaMeTrajectory = TrajectoryEntity.builder()
                .id(2)
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(deConfig))
                .linkMeEntities(new ArrayList<>())
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId))
                .thenReturn(new ArrayList<>());
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> linkMeCoherenceCheckService.validateLinkMeCoherence(studyId, trajectory));
        
        assertTrue(exception.getMessage().contains("AREAS") && exception.getMessage().contains("AREAS_ME"));
        assertFalse(exception.getErrorMessageArguments().isEmpty());
    }

    /**
     * Test RG2: nodeTo area not in AREA_ME - should throw BusinessException
     */
    @Test
    void validateLinkMeCoherence_nodeToNotInAreaMe_shouldThrowException() {
        // Arrange
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(List.of(LinkMeEntity.builder()
                        .nodeFrom("FR")
                        .nodeTo("MISSING_AREA")
                        .build()))
                .build();

        AreaEntity frArea = AreaEntity.builder().name("FR").build();
        AreaConfigEntity frConfig = new AreaConfigEntity("value1", 1.0, 2.0, frArea);
        TrajectoryEntity areaTrajectory = TrajectoryEntity.builder()
                .id(1)
                .type(TrajectoryType.AREA.name())
                .areaConfigEntities(List.of(frConfig))
                .linkMeEntities(new ArrayList<>())
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId))
                .thenReturn(List.of(areaTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(new ArrayList<>());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> linkMeCoherenceCheckService.validateLinkMeCoherence(studyId, trajectory));
        
        assertTrue(exception.getMessage().contains("AREAS_ME"));
        assertFalse(exception.getErrorMessageArguments().isEmpty());
    }

    /**
     * Test: Multiple missing areas - should list all in error message
     */
    @Test
    void validateLinkMeCoherence_multipleMissingNodeFromAreas_shouldThrowWithAllAreas() {
        // Arrange
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(List.of(
                        LinkMeEntity.builder().nodeFrom("MISSING1").nodeTo("DE").build(),
                        LinkMeEntity.builder().nodeFrom("MISSING2").nodeTo("DE").build()
                ))
                .build();

        AreaEntity deArea = AreaEntity.builder().name("DE").build();
        AreaConfigEntity deConfig = new AreaConfigEntity("value1", 1.0, 2.0, deArea);
        TrajectoryEntity areaMeTrajectory = TrajectoryEntity.builder()
                .id(2)
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(deConfig))
                .linkMeEntities(new ArrayList<>())
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId))
                .thenReturn(new ArrayList<>());
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> linkMeCoherenceCheckService.validateLinkMeCoherence(studyId, trajectory));
        
        assertNotNull(exception.getMessage());
        assertFalse(exception.getErrorMessageArguments().isEmpty());
    }

    /**
     * Test: nodeFrom in AREA_ME should also be valid (RG1 requires AREA or AREA_ME)
     */
    @Test
    void validateLinkMeCoherence_nodeFromInAreaMe_shouldPass() {
        // Arrange
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(List.of(LinkMeEntity.builder()
                        .nodeFrom("DE")
                        .nodeTo("FR")
                        .build()))
                .build();

        // DE in AREA_ME, FR in AREA_ME
        AreaEntity deArea = AreaEntity.builder().name("DE").build();
        AreaConfigEntity deConfig = new AreaConfigEntity("value1", 1.0, 2.0, deArea);
        
        AreaEntity frArea = AreaEntity.builder().name("FR").build();
        AreaConfigEntity frConfig = new AreaConfigEntity("value1", 1.0, 2.0, frArea);
        
        TrajectoryEntity areaMeTrajectory = TrajectoryEntity.builder()
                .id(2)
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(deConfig, frConfig))
                .linkMeEntities(new ArrayList<>())
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId))
                .thenReturn(new ArrayList<>());
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        // Act & Assert
        assertDoesNotThrow(() -> linkMeCoherenceCheckService.validateLinkMeCoherence(studyId, trajectory));
    }

    /**
     * Test: Case insensitive area matching
     */
    @Test
    void validateLinkMeCoherence_caseInsensitiveMatching_shouldPass() {
        // Arrange
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(List.of(LinkMeEntity.builder()
                        .nodeFrom("fr")  // lowercase
                        .nodeTo("de")    // lowercase
                        .build()))
                .build();

        AreaEntity frArea = AreaEntity.builder().name("FR").build();  // uppercase
        AreaConfigEntity frConfig = new AreaConfigEntity("value1", 1.0, 2.0, frArea);
        
        AreaEntity deArea = AreaEntity.builder().name("DE").build();  // uppercase
        AreaConfigEntity deConfig = new AreaConfigEntity("value1", 1.0, 2.0, deArea);
        
        TrajectoryEntity areaTrajectory = TrajectoryEntity.builder()
                .id(1)
                .type(TrajectoryType.AREA.name())
                .areaConfigEntities(List.of(frConfig))
                .linkMeEntities(new ArrayList<>())
                .build();

        TrajectoryEntity areaMeTrajectory = TrajectoryEntity.builder()
                .id(2)
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(deConfig))
                .linkMeEntities(new ArrayList<>())
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId))
                .thenReturn(List.of(areaTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        // Act & Assert
        assertDoesNotThrow(() -> linkMeCoherenceCheckService.validateLinkMeCoherence(studyId, trajectory));
    }
}
