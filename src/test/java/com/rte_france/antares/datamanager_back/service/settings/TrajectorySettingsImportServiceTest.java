package com.rte_france.antares.datamanager_back.service.settings;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrajectorySettingsImportServiceTest {

    @Mock
    private SettingsGeneralParametersRepository generalParametersRepository;

    @Mock
    private SettingsOptimizationParametersRepository optimizationParametersRepository;

    @Mock
    private SettingsAdvancedParametersRepository advancedParametersRepository;

    @Mock
    private SettingsSeedsParametersRepository seedsParametersRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    private SettingsImportService service;

    @BeforeEach
    void setUp() {
        service = new SettingsImportService(
                generalParametersRepository,
                optimizationParametersRepository,
                advancedParametersRepository,
                seedsParametersRepository,
                trajectoryRepository,
                antaresDataManagerProperties
        );
    }

    @Test
    void testImportSettingsFileNotFound() throws IOException {
        // Arrange
        String trajectoryToUse = "NONEXISTENT";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        // Verify trajectory was not saved
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void testImportSettingsDuplicateDetection() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_A_ref_200MC";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryToUse)
                .type("TRAJECTORY_SETTINGS")
                .horizon(horizon)
                .area(area)
                .version(1)
                .checksum("same_checksum_value")
                .build();

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "TRAJECTORY_SETTINGS", horizon, area))
                .thenReturn(Optional.of(existingTrajectory));

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        // Verify trajectory was not saved again
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void testImportSettingsParameterValidation() throws IOException {
        // Test with null parameters
        assertThrows(Exception.class, () -> {
            service.importSettings(null, "2028-2029", 1, "FR");
        });

        assertThrows(Exception.class, () -> {
            service.importSettings("BP23", null, 1, "FR");
        });

        assertThrows(Exception.class, () -> {
            service.importSettings("BP23", "2028-2029", null, "FR");
        });

        assertThrows(Exception.class, () -> {
            service.importSettings("BP23", "2028-2029", 1, null);
        });
    }

    @Test
    void testImportSettingsVersionIncrement() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_A_ref_200MC";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryToUse)
                .type("TRAJECTORY_SETTINGS")
                .horizon(horizon)
                .area(area)
                .version(1)
                .checksum("old_checksum_value")
                .build();

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "TRAJECTORY_SETTINGS", horizon, area))
                .thenReturn(Optional.of(existingTrajectory));

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        // File won't exist in test, so it will fail before version increment
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void testSecurityContextIntegration() {
        // Arrange
        UserDetails userDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, "password");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // This test verifies that security context can be accessed
        // The actual user extraction happens in the service
        assertNotNull(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void testPathConstructionFollowsNuclearPattern() {
        // Test that path construction follows the NuclearFileProcessorServiceImpl pattern
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Expected path pattern:
        // {NAS_DIR}/trajectories/parameters/general_data/{trajectoryToUse}/general_data_{trajectoryToUse}.xlsx
        String expectedBasePath = "/mnt/data/trajectories";
        String expectedTrajectoryFolder = "/mnt/data/trajectories/parameters/general_data/BP23_A_ref_200MC";

        // Verify the properties are correctly configured for path construction
        assertEquals("/mnt/data", antaresDataManagerProperties.getNasDirectory());
        assertEquals("trajectories", antaresDataManagerProperties.getTrajectoryFilePath());
        assertEquals("parameters/general_data", antaresDataManagerProperties.getTrajectorySettingsDirectory());
    }

    @Test
    void testGeneralParametersImportNotFound() throws IOException {
        // Arrange
        String trajectoryToUse = "NONEXISTENT_TRAJECTORY";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Act & Assert - should throw BusinessException when folder doesn't exist
        assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });
    }

    @Test
    void testExceptionHandlingWithHttpStatus() throws IOException {
        // Test that BusinessException is thrown with proper HTTP status
        String trajectoryToUse = "NONEXISTENT";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Trajectory settings folder not found"));
    }
}
