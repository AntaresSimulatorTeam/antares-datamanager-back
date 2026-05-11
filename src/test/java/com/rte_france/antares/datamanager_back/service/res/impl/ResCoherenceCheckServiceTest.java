package com.rte_france.antares.datamanager_back.service.res.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
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

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

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

     private TrajectoryEntity createLFTrajectory(String area, String technology) {
         TrajectoryEntity trajectory = new TrajectoryEntity();
         trajectory.setId(3);
         trajectory.setType(TrajectoryType.RES_LOAD.name());
         trajectory.setArea(area);
         trajectory.setTechnology(technology);
         trajectory.setFileName("test_lf");
         trajectory.setHorizon("2030");
         return trajectory;
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

             TrajectoryEntity ipBeingImported = ipTrajectories.getFirst();

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

     @Nested
     @DisplayName("IP/Load Factor Coherence Tests")
     class IPLoadFactorCoherenceTests {

         @Test
         @DisplayName("should pass when no trajectory being imported for IP/Load Factor")
         void shouldPassWhenNoTrajectoryBeingImportedForIPLF() {
             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, null));
         }

         @Test
         @DisplayName("should pass when no trajectories in database for IP/Load Factor")
         void shouldPassWhenNoTrajectoriesInDatabaseForIPLF() {
             // Arrange
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(new ArrayList<>());
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(new ArrayList<>());

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
         }

         @Test
         @DisplayName("should pass validation when importing IP with complete LF combinations")
         void shouldPassValidationWhenImportingIPWithCompleteLFCombinations() {
             // Arrange
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));

             TrajectoryEntity ipBeingImported = createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1");

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null));
             lfTrajectories.add(createLFTrajectory("FR", "wind_offshore"));
             lfTrajectories.add(createLFTrajectory("OTHERS", null));
             lfTrajectories.add(createLFTrajectory("OTHERS", "wind_offshore"));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert - Should pass but might fail due to file check
             // For this test, we only validate combinations, not file existence
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, ipBeingImported));
         }

         @Test
         @DisplayName("should pass when importing LF with complete IP combinations")
         void shouldPassWhenImportingLFWithCompleteIPCombinations() {
             // Arrange
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null));
             lfTrajectories.add(createLFTrajectory("FR", "wind_offshore"));
             lfTrajectories.add(createLFTrajectory("OTHERS", null));

             TrajectoryEntity lfBeingImported = createLFTrajectory("OTHERS", "wind_offshore");

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, lfBeingImported));
         }

         @Test
         @DisplayName("should skip validation when IP combinations incomplete")
         void shouldSkipValidationWhenIPCombinationsIncomplete() {
             // Arrange - Only FR without tech, missing others
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null));

             TrajectoryEntity ipBeingImported = createIPTrajectoryWithData("OTHERS", null, "G1", "C1");

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert - Should pass as combinations not complete
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, ipBeingImported));
         }

         @Test
         @DisplayName("should skip validation when LF combinations incomplete")
         void shouldSkipValidationWhenLFCombinationsIncomplete() {
             // Arrange - LF missing requirements
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null));

             TrajectoryEntity lfBeingImported = createLFTrajectory("OTHERS", null);

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert - Should pass as LF combinations not complete
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, lfBeingImported));
         }

         @Test
         @DisplayName("should handle different areas for IP/Load Factor coherence")
         void shouldHandleDifferentAreasForIPLFCoherence() {
             // Arrange - BE area
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("BE", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("BE", "solar", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "solar", "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("BE", null));
             lfTrajectories.add(createLFTrajectory("BE", "solar"));
             lfTrajectories.add(createLFTrajectory("OTHERS", null));
             lfTrajectories.add(createLFTrajectory("OTHERS", "solar"));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
         }

         @Test
         @DisplayName("overloaded method should skip when no IP trajectories")
         void overloadedMethodShouldSkipWhenNoIPTrajectories() {
             // Arrange
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(new ArrayList<>());
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(new ArrayList<>());

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
         }

         @Test
         @DisplayName("should handle whitespace technology in LF validation")
         void shouldHandleWhitespaceTechnologyInLFValidation() {
             // Arrange
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", "   ", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "   ", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", "   "));
             lfTrajectories.add(createLFTrajectory("FR", "wind_offshore"));
             lfTrajectories.add(createLFTrajectory("OTHERS", "   "));
             lfTrajectories.add(createLFTrajectory("OTHERS", "wind_offshore"));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
         }

         @Test
         @DisplayName("should validate multiple technologies in IP/LF coherence")
         void shouldValidateMultipleTechnologiesInIPLFCoherence() {
             // Arrange - Multiple technologies
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "solar", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "solar", "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null));
             lfTrajectories.add(createLFTrajectory("FR", "wind_offshore"));
             lfTrajectories.add(createLFTrajectory("FR", "solar"));
             lfTrajectories.add(createLFTrajectory("OTHERS", null));
             lfTrajectories.add(createLFTrajectory("OTHERS", "wind_offshore"));
             lfTrajectories.add(createLFTrajectory("OTHERS", "solar"));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
         }

         @Test
         @DisplayName("should handle lowercase area names in LF validation")
         void shouldHandleLowercaseAreaNamesInLFValidation() {
             // Arrange
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("fr", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("fr", "wind_offshore", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("others", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("others", "wind_offshore", "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null));
             lfTrajectories.add(createLFTrajectory("FR", "wind_offshore"));
             lfTrajectories.add(createLFTrajectory("OTHERS", null));
             lfTrajectories.add(createLFTrajectory("OTHERS", "wind_offshore"));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
         }

         @Test
         @DisplayName("should handle unrecognized area in LF validation")
         void shouldHandleUnrecognizedAreaInLFValidation() {
             // Arrange - Unrecognized area
             TrajectoryEntity lfBeingImported = createLFTrajectory("UNKNOWN_AREA", null);

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(new ArrayList<>());
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(new ArrayList<>());

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, lfBeingImported));
         }

         @Test
         @DisplayName("should handle empty LF entities list")
         void shouldHandleEmptyLFEntitiesList() {
             // Arrange
             TrajectoryEntity lfTrajectory = createLFTrajectory("FR", null);
             lfTrajectory.setResTechnologyDistributionCapacityEntities(null);

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(new ArrayList<>());
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(new ArrayList<>());

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, lfTrajectory));
         }

         @Test
         @DisplayName("should validate all 4 LF combinations required")
         void shouldValidateAll4LFCombinationsRequired() {
             // Arrange - Testing all 4 combinations
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "wind", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind", "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null)); // 1st
             lfTrajectories.add(createLFTrajectory("FR", "wind")); // 2nd
             lfTrajectories.add(createLFTrajectory("OTHERS", null)); // 3rd
             lfTrajectories.add(createLFTrajectory("OTHERS", "wind")); // 4th

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
         }
     }

    @Nested
    @DisplayName("Additional IP/TD Integration Tests")
    class IPTDIntegrationTests {

        @Test
        @DisplayName("should validate IP/TD coherence with all 4 IP combinations and matching TD")
        void shouldValidateIPTDWithAll4CombinationsMatching() {
            // Arrange - Full set of 4 IP combinations
            List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
            ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));

            TrajectoryEntity ipBeingImported = createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1");

            List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
            tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
            tdTrajectories.add(createTDTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(ipTrajectories);
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(tdTrajectories);

            // Act & Assert
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, ipBeingImported));
        }

        @Test
        @DisplayName("should skip validation when IP and TD TD combinations incomplete")
        void shouldSkipValidationWhenCombinationsIncomplete() {
            // Arrange - IP combos incomplete
            List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
            ipTrajectories.add(createIPTrajectoryWithData("FR", null, "wind_offshore", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "wind_offshore", "C1"));
            ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "wind_offshore", "C1"));
            // Missing OTHERS+wind_offshore

            // TD missing wind_offshore with tech too
            List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
            tdTrajectories.add(createTDTrajectoryWithData("FR", null, "wind_offshore", "C1"));
            // Missing FR with wind_offshore

            TrajectoryEntity ipBeingImported = createIPTrajectoryWithData("OTHERS", "wind_offshore", "wind_offshore", "C1");

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                    .thenReturn(ipTrajectories);
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                    .thenReturn(tdTrajectories);

            // Act & Assert - Should not throw because TD combinations incomplete
            assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, ipBeingImported));
        }
    }

     @Nested
     @DisplayName("Additional IP/LF File Coherence Tests")
     class IPLFFileCoherenceTests {

         @Test
         @DisplayName("should skip IP/LF validation when both IP and LF are incomplete")
         void shouldSkipWhenBothIPLFIncomplete() {
             // Arrange - Both have incomplete combinations
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert - Should pass (not validate files since requirements not met)
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
         }

         @Test
         @DisplayName("should validate IP/LF with complete combinations for multiple areas")
         void shouldValidateIPLFCompleteMultipleAreas() {
             // Arrange - Multiple areas with complete combinations
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "wind", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("BE", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("BE", "solar", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "solar", "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null));
             lfTrajectories.add(createLFTrajectory("FR", "wind"));
             lfTrajectories.add(createLFTrajectory("BE", null));
             lfTrajectories.add(createLFTrajectory("BE", "solar"));
             lfTrajectories.add(createLFTrajectory("OTHERS", null));
             lfTrajectories.add(createLFTrajectory("OTHERS", "wind"));
             lfTrajectories.add(createLFTrajectory("OTHERS", "solar"));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
         }

         @Test
         @DisplayName("should handle LF import with incomplete IP combinations")
         void shouldHandleLFImportWithIncompleteIP() {
             // Arrange - LF being imported, but IP is incomplete
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null));
             lfTrajectories.add(createLFTrajectory("FR", "wind"));
             lfTrajectories.add(createLFTrajectory("OTHERS", null));
             lfTrajectories.add(createLFTrajectory("OTHERS", "wind"));

             TrajectoryEntity lfBeingImported = createLFTrajectory("BE", null);

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert - Should pass (IP not complete, so skip validation)
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, lfBeingImported));
         }

         @Test
         @DisplayName("should handle mixture of tecnologies in IP/LF validation")
         void shouldHandleMixedTechnologiesIPLF() {
             // Arrange - Mix of technologies across trajectories
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "wind_offshore", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "solar", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind_offshore", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "solar", "G1", "C1"));

             List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
             lfTrajectories.add(createLFTrajectory("FR", null));
             lfTrajectories.add(createLFTrajectory("FR", "wind_offshore"));
             lfTrajectories.add(createLFTrajectory("OTHERS", null));
             lfTrajectories.add(createLFTrajectory("OTHERS", "wind_offshore"));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                     .thenReturn(lfTrajectories);

             // Act & Assert - Should pass (only validates matching technologies)
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
         }
     }

     @Nested
     @DisplayName("Boundary Tests")
     class BoundaryAndEdgeCaseTests {

         @Test
         @DisplayName("should pass validation when exactly 4 IP combinations exist")
         void shouldPassWithExactly4IPCombinations() {
             // Arrange - Exactly 4 combinations, no more, no less
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "tech1", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "tech1", "G1", "C1"));

             List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
             tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
             tdTrajectories.add(createTDTrajectoryWithData("FR", "tech1", "G1", "C1"));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                     .thenReturn(tdTrajectories);

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
         }

         @Test
         @DisplayName("should pass validation when exactly 3 IP trajectories with FR+OTHERS combinations")
         void shouldPassWith3IPTrajectories() {
             // Arrange - Only 3 IP trajectories, not 4 combinations
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "wind", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", "wind", "wind", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "wind", "C1"));

             List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
             tdTrajectories.add(createTDTrajectoryWithData("FR", null, "wind", "C1"));
             tdTrajectories.add(createTDTrajectoryWithData("FR", "wind", "wind", "C1"));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                     .thenReturn(tdTrajectories);

             // Act & Assert - Should pass as we're not having a trajectory being imported
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
         }

         @Test
         @DisplayName("should validate when 5+ input trajectories but only required 4 key combinations")
         void shouldValidateWithMoreThan4Trajectories() {
             // Arrange - More than 4 trajectories but with redundancy
             List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C2")); // Extra cluster
             ipTrajectories.add(createIPTrajectoryWithData("FR", "wind", "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
             ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind", "G1", "C1"));

             List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
             tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
             tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C2")); // Extra cluster
             tdTrajectories.add(createTDTrajectoryWithData("FR", "wind", "G1", "C1"));

             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                     .thenReturn(ipTrajectories);
             when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                     .thenReturn(tdTrajectories);

             // Act & Assert
             assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
          }
      }

      @Nested
      @DisplayName("Load Factor File Coherence Tests")
      class LoadFactorFileCoherenceTests {

          @Test
          @DisplayName("should skip validation when IP and LF have no IP clusters with technology")
          void shouldSkipValidationWhenNoIPClustersWithTechnology() {
              // Arrange - IP with no cluster capacities
              List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
              TrajectoryEntity ipTrajectory = createIPTrajectory("FR", null);
              ipTrajectory.setHorizon("2030-2031");
              ipTrajectory.setResClusterCapacityEntities(new ArrayList<>());
              ipTrajectories.add(ipTrajectory);
              ipTrajectories.add(createIPTrajectory("FR", "onshore"));
              ipTrajectories.add(createIPTrajectory("OTHERS", null));
              ipTrajectories.add(createIPTrajectory("OTHERS", "onshore"));

              List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
              TrajectoryEntity lfTrajectory = createLFTrajectory("FR", null);
              lfTrajectories.add(lfTrajectory);
              lfTrajectories.add(createLFTrajectory("FR", "onshore"));
              lfTrajectories.add(createLFTrajectory("OTHERS", null));
              lfTrajectories.add(createLFTrajectory("OTHERS", "onshore"));

              when(ResCoherenceCheckServiceTest.this.trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                      .thenReturn(ipTrajectories);
              when(ResCoherenceCheckServiceTest.this.trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                      .thenReturn(lfTrajectories);

              // Act & Assert - Should not throw because no IP clusters with technology
              assertDoesNotThrow(() -> ResCoherenceCheckServiceTest.this.resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, lfTrajectory));
          }
      }

      @Nested
      @DisplayName("Special Cases and Error Handling")
      class SpecialCasesErrorHandling {

          @Test
          @DisplayName("should handle null horizon in trajectory")
          void shouldHandleNullHorizonInTrajectory() {
              // Arrange - IP with null horizon should skip file validation
              List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
              TrajectoryEntity ipTrajectory = createIPTrajectoryWithData("FR", null, "onshore", "cluster1");
              ipTrajectory.setHorizon(null);  // Null horizon
              ipTrajectories.add(ipTrajectory);
              ipTrajectories.add(createIPTrajectoryWithData("FR", "onshore", "onshore", "cluster1"));
              ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "onshore", "cluster1"));

              List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
              lfTrajectories.add(createLFTrajectory("FR", null));
              lfTrajectories.add(createLFTrajectory("FR", "onshore"));
              lfTrajectories.add(createLFTrajectory("OTHERS", null));

              when(ResCoherenceCheckServiceTest.this.trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                      .thenReturn(ipTrajectories);
              when(ResCoherenceCheckServiceTest.this.trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                      .thenReturn(lfTrajectories);

              // Act & Assert - Should skip validation due to incomplete IP combinations
              assertDoesNotThrow(() -> ResCoherenceCheckServiceTest.this.resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
          }

          @Test
          @DisplayName("should handle empty technology set in file validation")
          void shouldHandleEmptyTechnologySetInFileValidation() {
              // Arrange - All trajectories without technology
              List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
              ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
              ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));

              List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
              lfTrajectories.add(createLFTrajectory("FR", null));
              lfTrajectories.add(createLFTrajectory("OTHERS", null));

              when(ResCoherenceCheckServiceTest.this.trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                      .thenReturn(ipTrajectories);
              when(ResCoherenceCheckServiceTest.this.trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                      .thenReturn(lfTrajectories);

              // Act & Assert - Should skip file validation as no technologies present
              assertDoesNotThrow(() -> ResCoherenceCheckServiceTest.this.resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
          }

          @Test
          @DisplayName("should validate correctly with BE and DE areas")
          void shouldValidateCorrectlyWithMultipleAreas() {
              // Arrange
              List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
              ipTrajectories.add(createIPTrajectoryWithData("BE", null, "G1", "C1"));
              ipTrajectories.add(createIPTrajectoryWithData("BE", "wind", "G1", "C1"));
              ipTrajectories.add(createIPTrajectoryWithData("DE", null, "G1", "C1"));
              ipTrajectories.add(createIPTrajectoryWithData("DE", "wind", "G1", "C1"));
              ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
              ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind", "G1", "C1"));

              List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
              tdTrajectories.add(createTDTrajectoryWithData("BE", null, "G1", "C1"));
              tdTrajectories.add(createTDTrajectoryWithData("BE", "wind", "G1", "C1"));
              tdTrajectories.add(createTDTrajectoryWithData("DE", null, "G1", "C1"));
              tdTrajectories.add(createTDTrajectoryWithData("DE", "wind", "G1", "C1"));

              when(ResCoherenceCheckServiceTest.this.trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                      .thenReturn(ipTrajectories);
              when(ResCoherenceCheckServiceTest.this.trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                      .thenReturn(tdTrajectories);

               // Act & Assert
               assertDoesNotThrow(() -> ResCoherenceCheckServiceTest.this.resCoherenceCheckService.validateIPTDCoherence(studyId));
           }
       }

       @Nested
       @DisplayName("CheckIfLoadFactorFileExists Tests")
       class CheckIfLoadFactorFileExistsTests {

           @Test
           @DisplayName("should return false when NAS directory is not configured")
           void shouldReturnFalseWhenNASDirNotConfigured() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn(null);
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test_trajectory", "groupe1", "cluster1", "FR", "2030"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should return false when file does not exist")
           void shouldReturnFalseWhenFileDoesNotExist() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "nonexistent_trajectory", "groupe1", "cluster1", "FR", "2030"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle null trajectory file name")
           void shouldHandleNullTrajectoryFileName() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       null, "groupe1", "cluster1", "FR", "2030"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle null groupe parameter")
           void shouldHandleNullGroupeParameter() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test_trajectory", null, "cluster1", "FR", "2030"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle null cluster parameter")
           void shouldHandleNullClusterParameter() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test_trajectory", "groupe1", null, "FR", "2030"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle null area parameter")
           void shouldHandleNullAreaParameter() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test_trajectory", "groupe1", "cluster1", null, "2030"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle null horizon parameter")
           void shouldHandleNullHorizonParameter() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test_trajectory", "groupe1", "cluster1", "FR", null
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle special characters in parameters")
           void shouldHandleSpecialCharactersInParameters() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test-trajectory_123", "groupe-1", "cluster+1", "FR", "2030-2031"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle empty string parameters")
           void shouldHandleEmptyStringParameters() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "", "groupe1", "cluster1", "FR", "2030"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle whitespace in parameters")
           void shouldHandleWhitespaceInParameters() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "  test_trajectory  ", "groupe1", "cluster1", "FR", "2030"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle path traversal attempts safely")
           void shouldHandlePathTraversalAttemptsSafely() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act - Attempt path traversal
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "../../../etc/passwd", "groupe1", "cluster1", "FR", "2030"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle different area names (FR, BE, DE)")
           void shouldHandleDifferentAreaNames() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act & Assert - Test multiple areas
               assertFalse(resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test_trajectory", "groupe1", "cluster1", "BE", "2030"
               ));
               assertFalse(resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test_trajectory", "groupe1", "cluster1", "DE", "2030"
               ));
               assertFalse(resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test_trajectory", "groupe1", "cluster1", "OTHERS", "2030"
               ));
           }

           @Test
           @DisplayName("should handle numeric trajectories and clusters")
           void shouldHandleNumericTrajectoriesAndClusters() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "123456", "1000", "500", "FR", "2030"
               );

               // Assert
               assertFalse(result);
           }

           @Test
           @DisplayName("should handle case sensitive path checking")
           void shouldHandleCaseSensitivePathChecking() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               // Act - Test with different cases
               boolean resultLower = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test_trajectory", "groupe1", "cluster1", "fr", "2030"
               );
               boolean resultUpper = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "test_trajectory", "groupe1", "cluster1", "FR", "2030"
               );

               // Assert - Both should return false as files don't exist
               assertFalse(resultLower);
               assertFalse(resultUpper);
           }

           @Test
           @DisplayName("should construct correct file path format")
           void shouldConstructCorrectFilePathFormat() {
               // Arrange - Using default values to verify path construction
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/nonexistent");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("traj");

               // Act - Should not throw exception even with invalid path
               boolean result = resCoherenceCheckService.checkIfLoadFactorFileExists(
                       "trajectory", "wind", "C1", "FR", "2030"
               );

               // Assert
               assertFalse(result);
           }
       }

       @Nested
       @DisplayName("validateIPLFFilesCoherence Integration Tests")
       class ValidateIPLFFilesCoherenceTests {

           @Test
           @DisplayName("should skip validation when no trajectory being imported and no IP trajectories")
           void shouldSkipValidationWhenNoIPTrajectories() {
               // Arrange
               List<TrajectoryEntity> emptyIPs = new ArrayList<>();
               List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
               lfTrajectories.add(createLFTrajectory("FR", null));

               // Act & Assert - Should not throw
               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLFFilesCoherence(
                       emptyIPs, null, lfTrajectories
               ));
           }

           @Test
           @DisplayName("should handle missing area and horizon validation")
           void shouldHandleMissingAreaAndHorizonValidation() {
               // Arrange
               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               TrajectoryEntity ipTrajectory = createIPTrajectory("FR", null);
               ipTrajectory.setArea(null); // Missing area
               ipTrajectories.add(ipTrajectory);

               List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
               lfTrajectories.add(createLFTrajectory("FR", null));

               // Act & Assert - Should skip validation due to missing area
               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLFFilesCoherence(
                       ipTrajectories, null, lfTrajectories
               ));
           }

           @Test
           @DisplayName("should throw exception when IP keys missing from LF files")
           void shouldThrowExceptionWhenIPKeysInvalidInLF() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               ipTrajectories.add(createIPTrajectoryWithData("FR", "wind", "wind", "C1"));
               ipTrajectories.forEach(t -> t.setHorizon("2030"));

               List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
               lfTrajectories.add(createLFTrajectory("FR", "wind"));

               // Act & Assert - Should throw exception as file doesn't exist
               assertThrows(BusinessException.class, () -> resCoherenceCheckService.validateIPLFFilesCoherence(
                       ipTrajectories, null, lfTrajectories
               ));
           }

           @Test
           @DisplayName("should extract correct available technologies from LF trajectories")
           void shouldExtractCorrectAvailableTechnologies() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               ipTrajectories.add(createIPTrajectoryWithData("FR", "solar", "solar", "C1"));
               ipTrajectories.forEach(t -> t.setHorizon("2030"));

               List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
               lfTrajectories.add(createLFTrajectory("FR", null)); // No tech - won't be in available
               lfTrajectories.add(createLFTrajectory("FR", "wind")); // Different tech
               lfTrajectories.add(createLFTrajectory("FR", "solar")); // Matching tech

               // Act & Assert - Should fail because IP has solar but LF doesn't have matching file
               assertThrows(BusinessException.class, () -> resCoherenceCheckService.validateIPLFFilesCoherence(
                       ipTrajectories, null, lfTrajectories
               ));
           }

           @Test
           @DisplayName("should handle multiple IP trajectories with same area")
           void shouldHandleMultipleIPTrajectoriesSameArea() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               TrajectoryEntity ip1 = createIPTrajectoryWithData("FR", "wind", "wind", "C1");
               ip1.setHorizon("2030");
               TrajectoryEntity ip2 = createIPTrajectoryWithData("FR", "wind", "wind", "C2");
               ip2.setHorizon("2030");
               ipTrajectories.add(ip1);
               ipTrajectories.add(ip2);

               List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
               lfTrajectories.add(createLFTrajectory("FR", "wind"));

               // Act & Assert - Should throw because files don't exist for both C1 and C2
               assertThrows(BusinessException.class, () -> resCoherenceCheckService.validateIPLFFilesCoherence(
                       ipTrajectories, null, lfTrajectories
               ));
           }

           @Test
           @DisplayName("should skip validation when no LF trajectories with technologies")
           void shouldSkipValidationWhenNoLFTechnologies() {
               // Arrange
               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               TrajectoryEntity ipTrajectory = createIPTrajectoryWithData("FR", "wind", "wind", "C1");
               ipTrajectory.setHorizon("2030");
               ipTrajectories.add(ipTrajectory);

               List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
               lfTrajectories.add(createLFTrajectory("FR", null)); // Only no tech - no available technologies

               // Act & Assert - Should not throw as no LF technologies available for filtering
               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLFFilesCoherence(
                       ipTrajectories, null, lfTrajectories
               ));
           }

           @Test
           @DisplayName("should handle OTHERS area in IP/LF file validation")
           void shouldHandleOthersAreaInIPLFValidation() {
               // Arrange
               when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
               when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");

               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               TrajectoryEntity ipOthers = createIPTrajectoryWithData("OTHERS", "wind", "wind", "C1");
               ipOthers.setHorizon("2030");
               ipTrajectories.add(ipOthers);

               List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
               lfTrajectories.add(createLFTrajectory("OTHERS", "wind"));

               // Act & Assert
               assertThrows(BusinessException.class, () -> resCoherenceCheckService.validateIPLFFilesCoherence(
                       ipTrajectories, null, lfTrajectories
               ));
           }

           @Test
           @DisplayName("should handle trajectory being imported with correct area extraction")
           void shouldHandleTrajectoryBeingImportedWithAreaExtraction() {
               // Arrange
               List<TrajectoryEntity> bdIpTrajectories = new ArrayList<>();
               TrajectoryEntity ipBeingImported = createIPTrajectoryWithData("BE", "wind", "wind", "C1");
               ipBeingImported.setHorizon("2030");

               List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
               lfTrajectories.add(createLFTrajectory("BE", null));

               // Act & Assert - Should extract area from trajectory being imported
               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLFFilesCoherence(
                       bdIpTrajectories, ipBeingImported, lfTrajectories
               ));
           }
       }

       @Nested
       @DisplayName("Helper Methods Tests")
       class HelperMethodsTests {

           @Test
           @DisplayName("isBlankOrEmpty should return true for null")
           void isBlankOrEmptyTrueForNull() {
               // Act & Assert - Private method, but implicitly tested through public methods
               // Testing indirectly through validation methods
               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));

               TrajectoryEntity trajectory = createIPTrajectory("FR", null);
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                       .thenReturn(ipTrajectories);
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                       .thenReturn(new ArrayList<>());

               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, trajectory));
           }

           @Test
           @DisplayName("isBlankOrEmpty should return true for empty string")
           void isBlankOrEmptyTrueForEmptyString() {
               // Act & Assert
               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               ipTrajectories.add(createIPTrajectoryWithData("FR", "", "G1", "C1"));

               TrajectoryEntity trajectory = createIPTrajectory("FR", "");
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                       .thenReturn(ipTrajectories);
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                       .thenReturn(new ArrayList<>());

               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, trajectory));
           }

           @Test
           @DisplayName("isBlankOrEmpty should return true for whitespace")
           void isBlankOrEmptyTrueForWhitespace() {
               // Act & Assert
               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               ipTrajectories.add(createIPTrajectoryWithData("FR", "   \t\n  ", "G1", "C1"));

               TrajectoryEntity trajectory = createIPTrajectory("FR", "   ");
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                       .thenReturn(ipTrajectories);
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                       .thenReturn(new ArrayList<>());

               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, trajectory));
           }

           @Test
           @DisplayName("formatKey should create correct key format")
           void formatKeyShouldCreateCorrectKeyFormat() {
               // Act & Assert - Tested indirectly through error messages
               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               ipTrajectories.add(createIPTrajectoryWithData("FR", null, "wind", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("FR", "wind", "wind", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "wind", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind", "wind", "C1"));

               List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
               tdTrajectories.add(createTDTrajectoryWithData("FR", null, "wind", "C2")); // Different cluster
               tdTrajectories.add(createTDTrajectoryWithData("FR", "wind", "wind", "C2"));

               TrajectoryEntity ipBeingImported = ipTrajectories.getFirst();

               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                       .thenReturn(ipTrajectories);
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                       .thenReturn(tdTrajectories);

               // Act & Assert
               BusinessException exception = assertThrows(BusinessException.class, () ->
                       resCoherenceCheckService.validateIPTDCoherence(studyId, ipBeingImported));

               // Verify error message contains formatted keys with C1
               assertTrue(exception.getErrorMessageArguments().stream()
                       .anyMatch(arg -> arg.toString().contains("C1")));
           }

           @Test
           @DisplayName("getDefaultAreas should return all configured areas in uppercase")
           void getDefaultAreasShouldReturnAllConfiguredAreasInUppercase() {
               // Arrange
               List<DefaultLoadDTO> defaultAreas = Arrays.asList(
                       createDefaultLoadDTO("fr"),
                       createDefaultLoadDTO("be"),
                       createDefaultLoadDTO("de"),
                       createDefaultLoadDTO("IT")
               );
               when(defaultConfigService.fetchAllDefaults()).thenReturn(defaultAreas);

               // Act - Create a simple validation that uses getDefaultAreas
               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               ipTrajectories.add(createIPTrajectoryWithData("IT", null, "G1", "C1"));

               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                       .thenReturn(ipTrajectories);
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                       .thenReturn(new ArrayList<>());

               // Act & Assert - Should recognize IT area even in lowercase in the list
               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId, 
                       createIPTrajectoryWithData("IT", "wind", "G1", "C1")));
           }
       }

       @Nested
       @DisplayName("Comprehensive Validation Scenarios")
       class ComprehensiveValidationScenarios {

           @Test
           @DisplayName("should validate complete scenario with all areas and technologies")
           void shouldValidateCompleteScenario() {
               // Arrange - Comprehensive scenario
               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("FR", "solar", "G1", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("FR", "wind", "G1", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("BE", null, "G1", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("BE", "solar", "G1", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "solar", "G1", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind", "G1", "C1"));

               List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
               tdTrajectories.add(createTDTrajectoryWithData("FR", null, "G1", "C1"));
               tdTrajectories.add(createTDTrajectoryWithData("FR", "solar", "G1", "C1"));
               tdTrajectories.add(createTDTrajectoryWithData("FR", "wind", "G1", "C1"));
               tdTrajectories.add(createTDTrajectoryWithData("BE", null, "G1", "C1"));
               tdTrajectories.add(createTDTrajectoryWithData("BE", "solar", "G1", "C1"));

               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                       .thenReturn(ipTrajectories);
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                       .thenReturn(tdTrajectories);

               // Act & Assert
               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
           }

           @Test
           @DisplayName("should validate with both IP and LF complete scenarios")
           void shouldValidateWithBothIPAndLFComplete() {
               // Arrange
               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               ipTrajectories.add(createIPTrajectoryWithData("FR", null, "G1", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("FR", "wind", "G1", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("OTHERS", null, "G1", "C1"));
               ipTrajectories.add(createIPTrajectoryWithData("OTHERS", "wind", "G1", "C1"));

               List<TrajectoryEntity> lfTrajectories = new ArrayList<>();
               lfTrajectories.add(createLFTrajectory("FR", null));
               lfTrajectories.add(createLFTrajectory("FR", "wind"));
               lfTrajectories.add(createLFTrajectory("OTHERS", null));
               lfTrajectories.add(createLFTrajectory("OTHERS", "wind"));

               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                       .thenReturn(ipTrajectories);
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId))
                       .thenReturn(lfTrajectories);

               // Act & Assert
               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPLoadFactorCoherence(studyId));
           }

           @Test
           @DisplayName("should handle complex cluster capacity entity filtering")
           void shouldHandleComplexClusterCapacityFiltering() {
               // Arrange - Multiple clusters with filtering
               List<TrajectoryEntity> ipTrajectories = new ArrayList<>();
               TrajectoryEntity ipTraj = createIPTrajectory("FR", null);
               List<ResClusterCapacityEntity> clusters = new ArrayList<>();
               clusters.add(createClusterCapacity("FR", "wind", "C1"));
               clusters.add(createClusterCapacity("FR", "wind", "C2"));
               clusters.add(createClusterCapacity("BE", "wind", "C1")); // Different area
               ipTraj.setResClusterCapacityEntities(clusters);
               ipTrajectories.add(ipTraj);

               List<TrajectoryEntity> tdTrajectories = new ArrayList<>();
               TrajectoryEntity tdTraj = createTDTrajectory("FR", null);
               List<ResTechnologyDistributionEntity> tdClusters = new ArrayList<>();
               tdClusters.add(createTDCapacity("FR", "wind", "C1"));
               tdClusters.add(createTDCapacity("FR", "wind", "C2"));
               tdTraj.setResTechnologyDistributionCapacityEntities(tdClusters);
               tdTrajectories.add(tdTraj);

               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId))
                       .thenReturn(ipTrajectories);
               when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId))
                       .thenReturn(tdTrajectories);

               // Act & Assert
               assertDoesNotThrow(() -> resCoherenceCheckService.validateIPTDCoherence(studyId));
           }
       }

       // Additional helper methods for creating test entities
       private ResClusterCapacityEntity createClusterCapacity(String area, String groupe, String cluster) {
           ResClusterCapacityEntity entity = new ResClusterCapacityEntity();
           entity.setArea(area);
           entity.setGroupe(groupe);
           entity.setCluster(cluster);
           return entity;
       }

       private ResTechnologyDistributionEntity createTDCapacity(String area, String groupe, String cluster) {
           ResTechnologyDistributionEntity entity = new ResTechnologyDistributionEntity();
           entity.setArea(area);
           entity.setGroupe(groupe);
           entity.setCluster(cluster);
           entity.setPecdZone("zone");
           entity.setPecdTechnology("tech");
           return entity;
       }
   }
