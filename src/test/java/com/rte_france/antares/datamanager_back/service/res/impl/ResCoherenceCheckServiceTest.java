package com.rte_france.antares.datamanager_back.service.res.impl;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ResClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.ResTechnologyDistributionEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.DefaultConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests for ResCoherenceCheckService
 * Validation de cohérence entre InstalledPower (IP) et Technology Distribution (TD)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ResCoherenceCheckServiceTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private DefaultConfigService defaultConfigService;

    @InjectMocks
    private ResCoherenceCheckService resCoherenceCheckService;

    private Integer studyId = 1;

    @BeforeEach
    void setUp() {
        studyId = 1;
        // Setup default areas
        List<DefaultLoadDTO> defaultAreas = Arrays.asList(
                createDefaultLoadDTO("FR"),
                createDefaultLoadDTO("BE"),
                createDefaultLoadDTO("DE")
        );
        when(defaultConfigService.fetchAllDefaults()).thenReturn(defaultAreas);
    }

    @Test
    void testValidationPassedWhenNoTrajectoryBeingImported() {
        // Arrange
        Integer studyId = 1;

        // Act & Assert - Should not throw exception when no trajectory being imported
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, null));
    }

    @Test
    void testValidationPassedWhenNoIPTrajectories() {
        // Arrange
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(new ArrayList<>());

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
    }

    @Test
    void testValidationPassedWhenNoTDTrajectories() {
        // Arrange
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectory("FR", null));

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(new ArrayList<>());

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
    }

    @Test
    void testValidationPassedWhenRequiredCombinationsNotPresent() {
        // Arrange - Only FR without tech, missing FR with tech and OTHERS
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectory("FR", null));

        List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
        tdTrajectories.add(createTDTrajectory("FR", null));

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(tdTrajectories);

        // Act & Assert - Should not throw exception (combinations not complete)
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
    }

    @Test
    void testValidationFailsWhenIPKeyMissingInTD() {
        // Arrange - Create IP trajectories with all required combinations
        // Note: groupe must match available technologies for keys to be extracted
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectoryWithData("FR", null, "wind_offshore", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "wind_offshore", "C1"));

        // Create TD trajectories - TD has the technology so groupe must match it
        List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
        tdTrajectories.add(createTDTrajectoryWithData("FR", null, "wind_offshore", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C1"));

        TrajectoryEntity othersWithTechTrajectory = createIPTrajectoryWithData("OTHERS", "wind_offshore", "wind_offshore", "C1");

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(tdTrajectories);

        // Act & Assert - Should throw exception for missing keys
        assertThrows(BusinessException.class, () -> resCoherenceCheckService.validateIPTDCoherence(studyId, othersWithTechTrajectory),
                "Should throw BusinessException when IP keys are missing in TD");
    }

    @Test
    void testValidationSucceedsWhenAllIPKeysExistInTD() {
        // Arrange
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));

        List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
        tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(tdTrajectories);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
    }

    @Test
    void testValidationWithIPTrajectoryBeingImportedAndCompleteRequiredCombinations() {
        // Arrange - Testing with trajectory being imported
        List<TrajectoryEntity> bdIpTrajectories = new ArrayList<>();
        bdIpTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
        bdIpTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
        bdIpTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));

        TrajectoryEntity ipBeingImported = createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1");

        List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
        tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(bdIpTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(tdTrajectories);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, ipBeingImported));
    }

    @Test
    void testValidationWithTDTrajectoryBeingImportedAndCompleteRequiredCombinations() {
        // Arrange - Testing with TD trajectory being imported
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));

        List<TrajectoryEntity> bdTdTrajectories = new ArrayList<>();
        bdTdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));

        TrajectoryEntity tdBeingImported = createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C1");

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(bdTdTrajectories);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, tdBeingImported));
    }

    @Test
    void testValidationWithDifferentArea() {
        // Arrange - Testing with non-FR area like BE
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectoryWithData("BE", null, "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("BE", "solar", "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "solar", "G1", "C1"));

        List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
        tdTrajectories.add(createTDTrajectoryWithData("BE", null, "G1", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("BE", "solar", "G1", "C1"));

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(tdTrajectories);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
    }

    @Test
    void testValidationIgnoresTDWithUnrecognizedArea() {
        // Arrange - TD with unrecognized area should be skipped
        TrajectoryEntity tdBeingImported = createTDTrajectoryWithData("UNKNOWN_AREA", null, "G1", "C1");

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(new ArrayList<>());
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(new ArrayList<>());

        // Act & Assert - Should not throw exception (area not recognized)
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, tdBeingImported));
    }

    @Test
    void testValidationFailsWhenIPHas4CombinationsButTDMissingOne() {
        // Arrange - IP has all 4 combinations, but TD is missing keys when we import an IP OTHERS
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectoryWithData("FR", null, "wind_offshore", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "wind_offshore", "C1"));

        // TD missing cluster C1 for OTHERS
        List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
        tdTrajectories.add(createTDTrajectoryWithData("FR", null, "wind_offshore", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C1"));

        // Import IP OTHERS with technology
        TrajectoryEntity othersWithTechTrajectory = createIPTrajectoryWithData("OTHERS", "wind_offshore", "wind_offshore", "C1");

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(tdTrajectories);

        // Act & Assert - Should throw exception for missing keys in TD
        assertThrows(BusinessException.class, () -> resCoherenceCheckService.validateIPTDCoherence(studyId, othersWithTechTrajectory),
                "Should throw BusinessException when TD keys are missing");
    }

    @Test
    void testValidationIgnoresIPWithIncompleteOthersCombinations() {
        // Arrange - IP missing the OTHERS+technology combination
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
        // Missing OTHERS+wind_offshore

        List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
        tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(tdTrajectories);

        // Act & Assert - Should not throw exception (IP combinations not complete, so no key validation)
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
    }

    @Test
    void testValidationWithMultipleTechnologies() {
        // Arrange - Testing with multiple technologies
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "solar", "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "solar", "G1", "C1"));

        List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
        tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "solar", "G1", "C1"));

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(tdTrajectories);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
    }

    @Test
    void testValidationWithMultipleGroupsAndClusters() {
        // Arrange - Testing with multiple groups and clusters
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C2"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G2", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C2"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G2", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C2"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G2", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C2"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G2", "C1"));

        List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
        tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C2"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G2", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C2"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G2", "C1"));

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(tdTrajectories);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
    }

    @Test
    void testValidationFailsWhenIPKeyNotFoundInTD() {
        // Arrange - IP has wind_offshore/C1 but TD has wind_offshore/C2
        List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
        ipTrajectories.add(createIPTrajectoryWithData("FR", null, "wind_offshore", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "wind_offshore", "C1"));
        ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "wind_offshore", "C1"));

        List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
        // TD with different clusters than IP
        tdTrajectories.add(createTDTrajectoryWithData("FR", null, "wind_offshore", "C2"));
        tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C2"));

        // Create a TD trajectory being imported
        TrajectoryEntity tdBeingImported = createTDTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C2");

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                .thenReturn(ipTrajectories);
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                .thenReturn(tdTrajectories);

        // Act & Assert - Should throw exception
        assertThrows(BusinessException.class, () -> resCoherenceCheckService.validateIPTDCoherence(studyId, tdBeingImported),
                "Should throw BusinessException for missing keys in TD");
    }

    // Helper methods

    private TrajectoryEntity createIPTrajectory(String area, String technology) {
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setType(TrajectoryType.RES_CAPACITY.name());
        trajectory.setArea(area);
        trajectory.setTechnology(technology);
        trajectory.setFileName("test_ip");
        trajectory.setResClusterCapacityEntities(new ArrayList<>());
        return trajectory;
    }

    private TrajectoryEntity createIPTrajectoryWithData(String area, String technology, String groupe, String cluster) {
        TrajectoryEntity trajectory = createIPTrajectory(area, technology);
        ResClusterCapacityEntity entity = new ResClusterCapacityEntity();
        entity.setArea(area);
        entity.setGroupe(groupe);
        entity.setCluster(cluster);
        trajectory.getResClusterCapacityEntities().add(entity);
        return trajectory;
    }

    private TrajectoryEntity createTDTrajectory(String area, String technology) {
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(2);
        trajectory.setType(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name());
        trajectory.setArea(area);
        trajectory.setTechnology(technology);
        trajectory.setFileName("test_td");
        trajectory.setResTechnologyDistributionCapacityEntities(new ArrayList<>());
        return trajectory;
    }

    private TrajectoryEntity createTDTrajectoryWithData(String area, String technology, String groupe, String cluster) {
        TrajectoryEntity trajectory = createTDTrajectory(area, technology);
        ResTechnologyDistributionEntity entity = new ResTechnologyDistributionEntity();
        entity.setArea(area);
        entity.setGroupe(groupe);
        entity.setCluster(cluster);
        entity.setPecdZone("zone1");
        entity.setPecdTechnology("pecd_tech");
        trajectory.getResTechnologyDistributionCapacityEntities().add(entity);
        return trajectory;
    }

    private DefaultLoadDTO createDefaultLoadDTO(String name) {
        DefaultLoadDTO dto = new DefaultLoadDTO();
        dto.setName(name);
        return dto;
    }

    @Nested
    @DisplayName("Case Sensitivity Tests")
    class CaseSensitivityTests {

        @Test
        @DisplayName("should handle lowercase area names")
        void shouldHandleLowercaseAreaNames() {
            // Arrange - IP with lowercase area
            List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
            ipTrajectories.add(createIPTrajectoryWithData("fr", null, "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("fr", "wind_offshore", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("others", null, "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("others", "wind_offshore", "G1", "C1"));

            List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
            tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
            tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(ipTrajectories);
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(tdTrajectories);

            // Act & Assert - Should handle case-insensitive comparison
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
        }

        @Test
        @DisplayName("should handle mixed case area names")
        void shouldHandleMixedCaseAreaNames() {
            // Arrange
            TrajectoryEntity ipTrajectory = createIPTrajectory("Fr", null);
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(new ArrayList<>());
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(new ArrayList<>());

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, ipTrajectory));
        }

        @Test
        @DisplayName("should handle uppercase OTHERS constant")
        void shouldHandleUppercaseOthersConstant() {
            // Arrange
            TrajectoryEntity ipOthers = createIPTrajectory("OTHERS", null);
            TrajectoryEntity ipFR = createIPTrajectory("FR", null);
            TrajectoryEntity ipFRWind = createIPTrajectory("FR", "wind_offshore");
            TrajectoryEntity ipOthersWind = createIPTrajectory("OTHERS", "wind_offshore");

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(List.of(ipFR, ipFRWind, ipOthers, ipOthersWind));
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(new ArrayList<>());

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, ipOthers));
        }
    }

    @Nested
    @DisplayName("Whitespace Handling Tests")
    class WhitespaceHandlingTests {

        @Test
        @DisplayName("should treat whitespace technology as blank")
        void shouldTreatWhitespaceTechnologyAsBlank() {
            // Arrange
            List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
            ipTrajectories.add(createIPTrajectoryWithData("FR", "   ", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "   ", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));

            List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
            tdTrajectories.add(createTDTrajectoryWithData("FR", "   ", "G1", "C1"));
            tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));

            TrajectoryEntity ipBeingImported = createIPTrajectoryWithData("FR", null, "G1", "C1");

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(ipTrajectories);
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(tdTrajectories);

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, ipBeingImported));
        }

        @Test
        @DisplayName("should handle tabs and spaces in technology")
        void shouldHandleTabsAndSpacesInTechnology() {
            // Arrange
            TrajectoryEntity ipTrajectory = createIPTrajectory("FR", "\t \n ");
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(new ArrayList<>());
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(new ArrayList<>());

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, ipTrajectory));
        }
    }

    @Nested
    @DisplayName("Overloaded Method Tests")
    class OverloadedMethodTests {

        @Test
        @DisplayName("should call validateIPTDCoherence with null trajectory when no trajectory parameter")
        void shouldCallValidateWithNullTrajectoryWhenNoParameter() {
            // Act & Assert - The overloaded method should just call the main method with null
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
        }

        @Test
        @DisplayName("overloaded method should skip when database trajectories incomplete")
        void overloadedMethodShouldSkipWhenDatabaseTrajectoriesIncomplete() {
            // Arrange
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(List.of(createIPTrajectory("FR", null)));
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(new ArrayList<>());

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
        }
    }

    @Nested
    @DisplayName("Empty and Null Collection Tests")
    class EmptyAndNullCollectionTests {

        @Test
        @DisplayName("should handle empty IP cluster entities list")
        void shouldHandleEmptyIPClusterEntitiesList() {
            // Arrange
            TrajectoryEntity ipTrajectory = createIPTrajectory("FR", null);
            ipTrajectory.setResClusterCapacityEntities(null);

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(new ArrayList<>());
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(new ArrayList<>());

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, ipTrajectory));
        }

        @Test
        @DisplayName("should handle empty TD capacity entities list")
        void shouldHandleEmptyTDCapacityEntitiesList() {
            // Arrange
            TrajectoryEntity tdTrajectory = createTDTrajectory("FR", null);
            tdTrajectory.setResTechnologyDistributionCapacityEntities(null);

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(new ArrayList<>());
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(new ArrayList<>());

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, tdTrajectory));
        }
    }

    @Nested
    @DisplayName("Complex Scenarios")
    class ComplexScenarios {

        @Test
        @DisplayName("should handle multiple areas with different technologies")
        void shouldHandleMultipleAreasWithDifferentTechnologies() {
            // Arrange - FR with Wind and Solar, BE with Wind only
            List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
            ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("FR", "solar", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("BE", null, "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("BE", "wind_offshore", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "solar", "G1", "C1"));

            List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
            tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
            tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
            tdTrajectories.add(createTDTrajectoryWithData("FR", "solar", "G1", "C1"));

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(ipTrajectories);
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(tdTrajectories);

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
        }

        @Test
        @DisplayName("should validate BE area with all 4 IP combinations")
        void shouldValidateBEAreaWithAll4IPCombinations() {
            // Arrange
            List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
            ipTrajectories.add(createIPTrajectoryWithData("BE", null, "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("BE", "wind_offshore", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));

            List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
            tdTrajectories.add(createTDTrajectoryWithData("BE", null, "G1", "C1"));
            tdTrajectories.add(createTDTrajectoryWithData("BE", "wind_offshore", "G1", "C1"));

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(ipTrajectories);
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(tdTrajectories);

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
        }

        @Test
        @DisplayName("should validate DE area trajectories")
        void shouldValidateDEAreaTrajectories() {
            // Arrange - DE is one of the default areas
            List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
            ipTrajectories.add(createIPTrajectoryWithData("DE", null, "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("DE", "wind_onshore", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_onshore", "G1", "C1"));

            List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
            tdTrajectories.add(createTDTrajectoryWithData("DE", null, "G1", "C1"));
            tdTrajectories.add(createTDTrajectoryWithData("DE", "wind_onshore", "G1", "C1"));

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(ipTrajectories);
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(tdTrajectories);

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
        }
    }

    @Nested
    @DisplayName("Error Message Tests")
    class ErrorMessageTests {

        @Test
        @DisplayName("should include missing keys in error message")
        void shouldIncludeMissingKeysInErrorMessage() {
            // Arrange
            List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
            ipTrajectories.add(createIPTrajectoryWithData("FR", null, "wind_offshore", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "wind_offshore", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "wind_offshore", "C1"));

            List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
            tdTrajectories.add(createTDTrajectoryWithData("FR", null, "wind_offshore", "C1"));
            tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C1"));

            // Create new IP to import that is not in the DB yet - import OTHERS null with new cluster
            TrajectoryEntity ipBeingImported = createIPTrajectoryWithData("OTHERS", null, "wind_offshore", "C2");

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(ipTrajectories);
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(tdTrajectories);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> resCoherenceCheckService.validateIPTDCoherence(studyId, ipBeingImported));
            assertTrue(exception.getMessage().contains("Clés manquantes"));
            // Check the error message arguments which contain the actual missing keys
            String errorArguments = String.join(",", exception.getErrorMessageArguments());
            assertTrue(errorArguments.contains("C2"));
        }

        @Test
        @DisplayName("error message should mention Technology Distribution")
        void errorMessageShouldMentionTechnologyDistribution() {
            // Arrange
            List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
            ipTrajectories.add(createIPTrajectoryWithData("FR", null, "wind_offshore", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "wind_offshore", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "wind_offshore", "C1"));

            List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
            tdTrajectories.add(createTDTrajectoryWithData("FR", null, "wind_offshore", "C2"));
            tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C2"));

            TrajectoryEntity ipBeingImported = ipTrajectories.get(0);

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(ipTrajectories);
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(tdTrajectories);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> resCoherenceCheckService.validateIPTDCoherence(studyId, ipBeingImported));
            assertTrue(exception.getMessage().contains("Technology Distribution"));
        }
    }
}

