package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.load.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests specifically for nuclear trajectory support
 */
@ExtendWith(MockitoExtension.class)
class NuclearTrajectoryServiceTest {

    @Mock
    private StudyTrajectoryRepository studyTrajectoryRepository;

    @InjectMocks
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private LoadRepository loadRepository;

    @Mock
    private UserService userService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private LoadFileProcessorServiceImpl loadFileProcessorServiceImpl;

    // ==================== NUCLEAR MODULATION TESTS (DIRECTORY) ====================

    @Test
    void getDirectoryByTrajectoryType_returnsNuclearModulationDirectory() throws IOException {
        // Given
        when(antaresDataManagerProperties.getNuclearModulationDirectory())
                .thenReturn("specific_nuclear/Modulation");

        // When
        String result = trajectoryService.getDirectoryByTrajectoryType(
                TrajectoryType.NUCLEAR_FR_MODULATION, null, null
        );

        // Then
        assertEquals("specific_nuclear/Modulation", result);
    }

    @Test
    void findTrajectoriesByType_returnsNuclearModulationDirectoriesWithFilter(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/modulation");
        Files.createDirectories(nuclearDir);
        
        Path mod_france = nuclearDir.resolve("modulation_france_2023-2024");
        Path mod_export = nuclearDir.resolve("modulation_export_2023-2024");
        Files.createDirectory(mod_france);
        Files.createDirectory(mod_export);
        
        // Add files inside to make directories non-empty
        Files.createFile(mod_france.resolve("data.txt"));
        Files.createFile(mod_export.resolve("data.txt"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getNuclearModulationDirectory()).thenReturn("nuclear/modulation");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(
                TrajectoryType.NUCLEAR_FR_MODULATION, null, null, "france"
        );

        // Then
        assertEquals(1, result.size());
        assertEquals("modulation_france_2023-2024", result.getFirst().getFileName());
    }

    // ==================== NUCLEAR TALON TESTS (FILE) ====================

    @Test
    void getDirectoryByTrajectoryType_returnsNuclearTalonDirectory() throws IOException {
        // Given
        when(antaresDataManagerProperties.getNuclearTalonDirectory())
                .thenReturn("specific_nuclear/Talon_nuc");

        // When
        String result = trajectoryService.getDirectoryByTrajectoryType(
                TrajectoryType.NUCLEAR_FR_TALON, null, null
        );

        // Then
        assertEquals("specific_nuclear/Talon_nuc", result);
    }

    // ==================== NUCLEAR EPR TESTS (FILE) ====================

    @Test
    void getDirectoryByTrajectoryType_returnsNuclearEprDirectory() throws IOException {
        // Given
        when(antaresDataManagerProperties.getNuclearEprDirectory())
                .thenReturn("specific_nuclear/TS_dispo/EPR");

        // When
        String result = trajectoryService.getDirectoryByTrajectoryType(
                TrajectoryType.NUCLEAR_FR_TS_ERP, null, null
        );

        // Then
        assertEquals("specific_nuclear/TS_dispo/EPR", result);
    }

    // ==================== NUCLEAR LT TESTS (DIRECTORY) ====================

    @Test
    void getDirectoryByTrajectoryType_returnsNuclearLtDirectory() throws IOException {
        // Given
        when(antaresDataManagerProperties.getNuclearLtDirectory())
                .thenReturn("specific_nuclear/TS_dispo");

        // When
        String result = trajectoryService.getDirectoryByTrajectoryType(
                TrajectoryType.NUCLEAR_FR_TS_LONG_TERM, null, null
        );

        // Then
        assertEquals("specific_nuclear/TS_dispo", result);
    }

    @Test
    void findTrajectoriesByType_returnsNuclearLtDirectoriesInNestedPath(@TempDir Path tempDir) throws IOException {
        // Given
        Path ltDir = tempDir.resolve("ts_dispo");
        Files.createDirectories(ltDir);
        
        Path lt2023 = ltDir.resolve("lt_2023");
        Path lt2024 = ltDir.resolve("lt_2024");
        Files.createDirectory(lt2023);
        Files.createDirectory(lt2024);
        
        // Add files inside to make directories non-empty
        Files.createFile(lt2023.resolve("data.txt"));
        Files.createFile(lt2024.resolve("data.txt"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getNuclearLtDirectory()).thenReturn("ts_dispo");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(
                TrajectoryType.NUCLEAR_FR_TS_LONG_TERM, null, null, null
        );

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(dto -> dto.getType().equals("NUCLEAR_FR_TS_LONG_TERM")));
    }

    // ==================== NUCLEAR SMR TESTS (FILE) ====================

    @Test
    void getDirectoryByTrajectoryType_returnsNuclearSmrDirectory() throws IOException {
        // Given
        when(antaresDataManagerProperties.getNuclearSmrDirectory())
                .thenReturn("specific_nuclear/TS_dispo/SMR");

        // When
        String result = trajectoryService.getDirectoryByTrajectoryType(
                TrajectoryType.NUCLEAR_FR_TS_SMR, null, null
        );

        // Then
        assertEquals("specific_nuclear/TS_dispo/SMR", result);
    }

    // ==================== NUCLEAR INTEGRATION TESTS ====================

    @Test
    void findTrajectoriesByType_nuclearModulationIsRecognizedAsDirectory(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/modulation");
        Files.createDirectories(nuclearDir);
        
        Path modDir = nuclearDir.resolve("modulation_2023-2024");
        Files.createDirectory(modDir);
        
        // Add a file inside to verify it's a directory
        Files.createFile(modDir.resolve("data.txt"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getNuclearModulationDirectory()).thenReturn("nuclear/modulation");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(
                TrajectoryType.NUCLEAR_FR_MODULATION, null, null, null
        );

        // Then
        assertEquals(1, result.size());
        assertEquals("modulation_2023-2024", result.getFirst().getFileName());
        assertEquals("NUCLEAR_FR_MODULATION", result.getFirst().getType());
    }

    @Test
    void findTrajectoriesByType_nuclearLtIsRecognizedAsDirectory(@TempDir Path tempDir) throws IOException {
        // Given
        Path ltDir = tempDir.resolve("ts_dispo");
        Files.createDirectories(ltDir);
        
        Path ltSubDir = ltDir.resolve("lt_2023-2024");
        Files.createDirectory(ltSubDir);
        
        // Add a file inside to verify it's a directory
        Files.createFile(ltSubDir.resolve("configuration.xml"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getNuclearLtDirectory()).thenReturn("ts_dispo");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(
                TrajectoryType.NUCLEAR_FR_TS_LONG_TERM, null, null, null
        );

        // Then
        assertEquals(1, result.size());
        assertEquals("lt_2023-2024", result.getFirst().getFileName());
        assertEquals("NUCLEAR_FR_TS_LONG_TERM", result.getFirst().getType());
    }

    @Test
    void findTrajectoriesByType_nuclearModulationFilterByFileName(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/modulation");
        Files.createDirectories(nuclearDir);
        
        Path mod_2023 = nuclearDir.resolve("modulation_2023-2024");
        Path mod_2024 = nuclearDir.resolve("modulation_2024-2025");
        Path mod_other = nuclearDir.resolve("modulation_other");
        Files.createDirectory(mod_2023);
        Files.createDirectory(mod_2024);
        Files.createDirectory(mod_other);
        
        // Add files inside to make directories non-empty
        Files.createFile(mod_2023.resolve("data.txt"));
        Files.createFile(mod_2024.resolve("data.txt"));
        Files.createFile(mod_other.resolve("data.txt"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getNuclearModulationDirectory()).thenReturn("nuclear/modulation");

        // When - filter by "2023"
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(
                TrajectoryType.NUCLEAR_FR_MODULATION, null, null, "2023"
        );

        // Then
        assertEquals(1, result.size());
        assertEquals("modulation_2023-2024", result.getFirst().getFileName());
    }

    @Test
    void normalizeAndValidateDirectory_forNuclearModulation(@TempDir Path tempDir) throws IOException {
        // Given
        Path baseDir = tempDir.resolve("INPUT");
        Files.createDirectories(baseDir);
        Path nuclearDir = baseDir.resolve("specific_nuclear/Modulation");
        Files.createDirectories(nuclearDir);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("INPUT");
        when(antaresDataManagerProperties.getNuclearModulationDirectory()).thenReturn("specific_nuclear/Modulation");

        // When
        Path result = trajectoryService.normalizeAndValidateDirectory(
                TrajectoryType.NUCLEAR_FR_MODULATION, null, null
        );

        // Then
        assertTrue(Files.exists(result));
        assertTrue(result.toString().contains("specific_nuclear") && result.toString().contains("Modulation"));
    }

    @Test
    void normalizeAndValidateDirectory_forNuclearEpr(@TempDir Path tempDir) throws IOException {
        // Given
        Path baseDir = tempDir.resolve("INPUT");
        Files.createDirectories(baseDir);
        Path eprDir = baseDir.resolve("specific_nuclear/TS_dispo/EPR");
        Files.createDirectories(eprDir);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("INPUT");
        when(antaresDataManagerProperties.getNuclearEprDirectory()).thenReturn("specific_nuclear/TS_dispo/EPR");

        // When
        Path result = trajectoryService.normalizeAndValidateDirectory(
                TrajectoryType.NUCLEAR_FR_TS_ERP, null, null
        );

        // Then
        assertTrue(Files.exists(result));
        assertTrue(result.toString().contains("EPR"));
    }

}


