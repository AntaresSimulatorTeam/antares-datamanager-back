package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.HydroCapacityMeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HydroMeFileProcessorServiceImplTest {

    @Mock(lenient = true)
    private TrajectoryRepository trajectoryRepository;

    @Mock(lenient = true)
    private UserService userService;

    @Mock(lenient = true)
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock(lenient = true)
    private HydroCapacityMeRepository hydroCapacityMeRepository;

    @InjectMocks
    private HydroMeFileProcessorServiceImpl hydroMeFileProcessorService;

    @TempDir
    Path tempDir;

    private String testTrajectoryName;
    private String testHorizon;
    private Path testExcelPath;

    @BeforeEach
    void setUp() throws IOException {
        testTrajectoryName = "test_hydro";
        testHorizon = "2020-2021";
        
        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());
        
        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(tempDir.toString());
        
        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");
        
        when(antaresDataManagerProperties.getHydroCapacityMeDirectory())
                .thenReturn("ME/hydro_ME");
        
        setupExcelFile();
    }

    private void setupExcelFile() throws IOException {
        Path capaStoragePath = tempDir.resolve("trajectories/ME/hydro_ME");
        Files.createDirectories(capaStoragePath);
        testExcelPath = capaStoragePath.resolve(testTrajectoryName + ".xlsx");
    }

    @Test
    void testProcessHydroCapacityMeFile_Success() throws IOException {
        createValidExcelFile(testExcelPath);
        
        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .horizon(testHorizon)
                .type(TrajectoryType.HYDRO_CAPACITY_ME.name())
                .version(1)
                .checksum("test_checksum")
                .build();
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(mockTrajectory);
        
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroCapacityMeFile(testTrajectoryName, testHorizon, 1);
        
        assertNotNull(result);
        assertEquals(testTrajectoryName, result.getFileName());
        assertEquals(1, result.getVersion());
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void testProcessHydroCapacityMeFile_MissingHorizonSheet() throws IOException {
        createExcelFileWithoutHorizonSheet();
        
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> hydroMeFileProcessorService.processHydroCapacityMeFile(testTrajectoryName, testHorizon, 1));
        
        assertTrue(exception.getMessage().contains("Missing horizon"));
        assertTrue(exception.getMessage().contains("2021"));
    }

    @Test
    void testProcessHydroCapacityMeFile_NodeColumnExceeds60Chars() throws IOException {
        createExcelFileWithLongNode();
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> hydroMeFileProcessorService.processHydroCapacityMeFile(testTrajectoryName, testHorizon, 1));
        
        assertTrue(exception.getMessage().contains("cannot exceed 60 characters"));
    }

    @Test
    void testProcessHydroCapacityMeFile_InvalidTimestepValue() throws IOException {
        createExcelFileWithInvalidTimestep();
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> hydroMeFileProcessorService.processHydroCapacityMeFile(testTrajectoryName, testHorizon, 1));
        
        assertTrue(exception.getMessage().contains("must be 'annual' or 'daily' only"));
    }

    @Test
    void testProcessHydroCapacityMeFile_NonNumericColumn() throws IOException {
        createExcelFileWithNonNumericValue();
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> hydroMeFileProcessorService.processHydroCapacityMeFile(testTrajectoryName, testHorizon, 1));
        
        assertTrue(exception.getMessage().contains("must be numeric"));
    }

    @Test
    void testProcessHydroCapacityMeFile_VersionIncrement() throws IOException {
        createValidExcelFile(testExcelPath);
        
        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .horizon(testHorizon)
                .type(TrajectoryType.HYDRO_CAPACITY_ME.name())
                .version(2)
                .checksum("old_checksum")
                .build();
        
        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(2)
                .fileName(testTrajectoryName)
                .horizon(testHorizon)
                .type(TrajectoryType.HYDRO_CAPACITY_ME.name())
                .version(3)
                .checksum("new_checksum")
                .build();
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(existingTrajectory));
        
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(newTrajectory);
        
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroCapacityMeFile(testTrajectoryName, testHorizon, 1);
        
        assertEquals(3, result.getVersion());
    }

    @Test
    void testProcessHydroCapacityMeFile_MissingUserNni() throws IOException {
        createValidExcelFile(testExcelPath);
        
        when(userService.getCurrentUserDetails())
                .thenReturn(null);
        
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> hydroMeFileProcessorService.processHydroCapacityMeFile(testTrajectoryName, testHorizon, 1));
        
        assertTrue(exception.getMessage().contains("User NNI could not be determined"));
    }

    @Test
    void testProcessHydroCapacityMeFile_MissingConfiguration() throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(null);
        
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> hydroMeFileProcessorService.processHydroCapacityMeFile(testTrajectoryName, testHorizon, 1));
        
        assertTrue(exception.getMessage().contains("Antares path configuration is incomplete"));
    }

    private void createValidExcelFile(Path excelPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2021");
            
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            headerRow.createCell(1).setCellValue("Reservoir Capacity [MWh]");
            headerRow.createCell(2).setCellValue("Generating Pmax - timestep (daily/annual)");
            headerRow.createCell(3).setCellValue("Generating Pmax [MW]");
            headerRow.createCell(4).setCellValue("hours at generating Pmax");
            headerRow.createCell(5).setCellValue("Pumping Pmax - timestep (daily/annual)");
            headerRow.createCell(6).setCellValue("Pumping Pmax [MW]");
            headerRow.createCell(7).setCellValue("hours at pumping Pmax");
            
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("AREA_1");
            dataRow.createCell(1).setCellValue(1000.0);
            dataRow.createCell(2).setCellValue("annual");
            dataRow.createCell(3).setCellValue(500.0);
            dataRow.createCell(4).setCellValue(24.0);
            dataRow.createCell(5).setCellValue("annual");
            dataRow.createCell(6).setCellValue(300.0);
            dataRow.createCell(7).setCellValue(12.0);
            
            Files.createDirectories(excelPath.getParent());
            try (var fos = Files.newOutputStream(excelPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createExcelFileWithoutHorizonSheet() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2020");
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            
            Files.createDirectories(testExcelPath.getParent());
            try (var fos = Files.newOutputStream(testExcelPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createExcelFileWithLongNode() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2021");
            
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            headerRow.createCell(1).setCellValue("Reservoir Capacity [MWh]");
            headerRow.createCell(2).setCellValue("Generating Pmax - timestep (daily/annual)");
            headerRow.createCell(3).setCellValue("Generating Pmax [MW]");
            headerRow.createCell(4).setCellValue("hours at generating Pmax");
            headerRow.createCell(5).setCellValue("Pumping Pmax - timestep (daily/annual)");
            headerRow.createCell(6).setCellValue("Pumping Pmax [MW]");
            headerRow.createCell(7).setCellValue("hours at pumping Pmax");
            
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("A".repeat(61));
            dataRow.createCell(1).setCellValue(1000.0);
            dataRow.createCell(2).setCellValue("annual");
            dataRow.createCell(3).setCellValue(500.0);
            dataRow.createCell(4).setCellValue(24.0);
            dataRow.createCell(5).setCellValue("annual");
            dataRow.createCell(6).setCellValue(300.0);
            dataRow.createCell(7).setCellValue(12.0);
            
            Files.createDirectories(testExcelPath.getParent());
            try (var fos = Files.newOutputStream(testExcelPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createExcelFileWithInvalidTimestep() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2021");
            
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            headerRow.createCell(1).setCellValue("Reservoir Capacity [MWh]");
            headerRow.createCell(2).setCellValue("Generating Pmax - timestep (daily/annual)");
            headerRow.createCell(3).setCellValue("Generating Pmax [MW]");
            headerRow.createCell(4).setCellValue("hours at generating Pmax");
            headerRow.createCell(5).setCellValue("Pumping Pmax - timestep (daily/annual)");
            headerRow.createCell(6).setCellValue("Pumping Pmax [MW]");
            headerRow.createCell(7).setCellValue("hours at pumping Pmax");
            
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("AREA_1");
            dataRow.createCell(1).setCellValue(1000.0);
            dataRow.createCell(2).setCellValue("invalid");
            dataRow.createCell(3).setCellValue(500.0);
            dataRow.createCell(4).setCellValue(24.0);
            dataRow.createCell(5).setCellValue("annual");
            dataRow.createCell(6).setCellValue(300.0);
            dataRow.createCell(7).setCellValue(12.0);
            
            Files.createDirectories(testExcelPath.getParent());
            try (var fos = Files.newOutputStream(testExcelPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createExcelFileWithNonNumericValue() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2021");
            
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            headerRow.createCell(1).setCellValue("Reservoir Capacity [MWh]");
            headerRow.createCell(2).setCellValue("Generating Pmax - timestep (daily/annual)");
            headerRow.createCell(3).setCellValue("Generating Pmax [MW]");
            headerRow.createCell(4).setCellValue("hours at generating Pmax");
            headerRow.createCell(5).setCellValue("Pumping Pmax - timestep (daily/annual)");
            headerRow.createCell(6).setCellValue("Pumping Pmax [MW]");
            headerRow.createCell(7).setCellValue("hours at pumping Pmax");
            
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("AREA_1");
            dataRow.createCell(1).setCellValue("not_numeric");
            dataRow.createCell(2).setCellValue("annual");
            dataRow.createCell(3).setCellValue(500.0);
            dataRow.createCell(4).setCellValue(24.0);
            dataRow.createCell(5).setCellValue("annual");
            dataRow.createCell(6).setCellValue(300.0);
            dataRow.createCell(7).setCellValue(12.0);
            
            Files.createDirectories(testExcelPath.getParent());
            try (var fos = Files.newOutputStream(testExcelPath)) {
                workbook.write(fos);
            }
        }
    }

    @Test
    void testProcessHydroCapacityMeFile_TrackDailyGeneratingTimestep(@TempDir Path uniqueTempDir) throws IOException {
        String testName = "test_hydro_gen";
        Path capaStoragePath = uniqueTempDir.resolve("trajectories/ME/hydro_ME");
        Files.createDirectories(capaStoragePath);
        Path testExcelPath = capaStoragePath.resolve(testName + ".xlsx");
        
        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());
        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(uniqueTempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");
        when(antaresDataManagerProperties.getHydroCapacityMeDirectory())
                .thenReturn("ME/hydro_ME");
        
        createExcelFileWithDailyGeneratingTimestepAtPath(testExcelPath);
        
        Path generatingDailyTsDir = capaStoragePath.resolve("Generating Pmax daily ts");
        Files.createDirectories(generatingDailyTsDir);
        Files.createFile(generatingDailyTsDir.resolve(testName + ".xlsx"));
        
        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testName)
                .horizon(testHorizon)
                .type(TrajectoryType.HYDRO_CAPACITY_ME.name())
                .version(1)
                .checksum("test_checksum")
                .build();
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(mockTrajectory);
        
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroCapacityMeFile(testName, testHorizon, 1);
        
        assertNotNull(result);
        assertEquals(testName, result.getFileName());
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void testProcessHydroCapacityMeFile_TrackDailyPumpingTimestep(@TempDir Path uniqueTempDir) throws IOException {
        String testName = "test_hydro_pump";
        Path capaStoragePath = uniqueTempDir.resolve("trajectories/ME/hydro_ME");
        Files.createDirectories(capaStoragePath);
        Path testExcelPath = capaStoragePath.resolve(testName + ".xlsx");
        
        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());
        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(uniqueTempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");
        when(antaresDataManagerProperties.getHydroCapacityMeDirectory())
                .thenReturn("ME/hydro_ME");
        
        createExcelFileWithDailyPumpingTimestepAtPath(testExcelPath);
        
        Path pumpingDailyTsDir = capaStoragePath.resolve("Pumping Pmax daily ts");
        Files.createDirectories(pumpingDailyTsDir);
        Files.createFile(pumpingDailyTsDir.resolve(testName + ".xlsx"));
        
        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testName)
                .horizon(testHorizon)
                .type(TrajectoryType.HYDRO_CAPACITY_ME.name())
                .version(1)
                .checksum("test_checksum")
                .build();
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(mockTrajectory);
        
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroCapacityMeFile(testName, testHorizon, 1);
        
        assertNotNull(result);
        assertEquals(testName, result.getFileName());
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void testProcessHydroCapacityMeFile_TrackBothDailyTimesteps(@TempDir Path uniqueTempDir) throws IOException {
        String testName = "test_hydro_both";
        Path capaStoragePath = uniqueTempDir.resolve("trajectories/ME/hydro_ME");
        Files.createDirectories(capaStoragePath);
        Path testExcelPath = capaStoragePath.resolve(testName + ".xlsx");
        
        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());
        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(uniqueTempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");
        when(antaresDataManagerProperties.getHydroCapacityMeDirectory())
                .thenReturn("ME/hydro_ME");
        
        createExcelFileWithBothDailyTimestepsAtPath(testExcelPath);
        
        Path generatingDailyTsDir = capaStoragePath.resolve("Generating Pmax daily ts");
        Path pumpingDailyTsDir = capaStoragePath.resolve("Pumping Pmax daily ts");
        Files.createDirectories(generatingDailyTsDir);
        Files.createDirectories(pumpingDailyTsDir);
        Files.createFile(generatingDailyTsDir.resolve(testName + ".xlsx"));
        Files.createFile(pumpingDailyTsDir.resolve(testName + ".xlsx"));
        
        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testName)
                .horizon(testHorizon)
                .type(TrajectoryType.HYDRO_CAPACITY_ME.name())
                .version(1)
                .checksum("test_checksum")
                .build();
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(mockTrajectory);
        
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroCapacityMeFile(testName, testHorizon, 1);
        
        assertNotNull(result);
        assertEquals(testName, result.getFileName());
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void testProcessHydroCapacityMeFile_MissingGeneratingDailyTimeSeriesDirectory(@TempDir Path uniqueTempDir) throws IOException {
        String testName = "test_hydro_miss_gen_dir";
        Path capaStoragePath = uniqueTempDir.resolve("trajectories/ME/hydro_ME");
        Files.createDirectories(capaStoragePath);
        Path testExcelPath = capaStoragePath.resolve(testName + ".xlsx");
        
        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());
        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(uniqueTempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");
        when(antaresDataManagerProperties.getHydroCapacityMeDirectory())
                .thenReturn("ME/hydro_ME");
        
        createExcelFileWithDailyGeneratingTimestepAtPath(testExcelPath);
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> hydroMeFileProcessorService.processHydroCapacityMeFile(testName, testHorizon, 1));
        
        assertTrue(exception.getMessage().contains("Missing") && exception.getMessage().contains("Generating Pmax daily ts"));
    }

    @Test
    void testProcessHydroCapacityMeFile_MissingPumpingDailyTimeSeriesDirectory(@TempDir Path uniqueTempDir) throws IOException {
        String testName = "test_hydro_miss_pump_dir";
        Path capaStoragePath = uniqueTempDir.resolve("trajectories/ME/hydro_ME");
        Files.createDirectories(capaStoragePath);
        Path testExcelPath = capaStoragePath.resolve(testName + ".xlsx");
        
        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());
        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(uniqueTempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");
        when(antaresDataManagerProperties.getHydroCapacityMeDirectory())
                .thenReturn("ME/hydro_ME");
        
        createExcelFileWithDailyPumpingTimestepAtPath(testExcelPath);
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> hydroMeFileProcessorService.processHydroCapacityMeFile(testName, testHorizon, 1));
        
        assertTrue(exception.getMessage().contains("Missing") && exception.getMessage().contains("Pumping Pmax daily ts"));
    }

    @Test
    void testProcessHydroCapacityMeFile_MissingGeneratingDailyTimeSeriesFile(@TempDir Path uniqueTempDir) throws IOException {
        String testName = "test_hydro_miss_gen_file";
        Path capaStoragePath = uniqueTempDir.resolve("trajectories/ME/hydro_ME");
        Files.createDirectories(capaStoragePath);
        Path testExcelPath = capaStoragePath.resolve(testName + ".xlsx");
        
        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());
        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(uniqueTempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");
        when(antaresDataManagerProperties.getHydroCapacityMeDirectory())
                .thenReturn("ME/hydro_ME");
        
        createExcelFileWithDailyGeneratingTimestepAtPath(testExcelPath);
        
        Path generatingDailyTsDir = uniqueTempDir.resolve("Generating Pmax daily ts");
        Files.createDirectories(generatingDailyTsDir);
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> hydroMeFileProcessorService.processHydroCapacityMeFile(testName, testHorizon, 1));
        
        assertTrue(exception.getMessage().contains("Missing") && exception.getMessage().contains("Generating Pmax"));
    }

    @Test
    void testProcessHydroCapacityMeFile_MissingPumpingDailyTimeSeriesFile(@TempDir Path uniqueTempDir) throws IOException {
        String testName = "test_hydro_miss_pump_file";
        Path capaStoragePath = uniqueTempDir.resolve("trajectories/ME/hydro_ME");
        Files.createDirectories(capaStoragePath);
        Path testExcelPath = capaStoragePath.resolve(testName + ".xlsx");
        
        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());
        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(uniqueTempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");
        when(antaresDataManagerProperties.getHydroCapacityMeDirectory())
                .thenReturn("ME/hydro_ME");
        
        createExcelFileWithDailyPumpingTimestepAtPath(testExcelPath);
        
        Path pumpingDailyTsDir = capaStoragePath.resolve("Pumping Pmax daily ts");
        Files.createDirectories(pumpingDailyTsDir);
        
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> hydroMeFileProcessorService.processHydroCapacityMeFile(testName, testHorizon, 1));
        
        assertTrue(exception.getMessage().contains("Missing") && exception.getMessage().contains("Pumping Pmax"));
    }

    private void createExcelFileWithDailyGeneratingTimestep() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2021");
            
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            headerRow.createCell(1).setCellValue("Reservoir Capacity [MWh]");
            headerRow.createCell(2).setCellValue("Generating Pmax - timestep (daily/annual)");
            headerRow.createCell(3).setCellValue("Generating Pmax [MW]");
            headerRow.createCell(4).setCellValue("hours at generating Pmax");
            headerRow.createCell(5).setCellValue("Pumping Pmax - timestep (daily/annual)");
            headerRow.createCell(6).setCellValue("Pumping Pmax [MW]");
            headerRow.createCell(7).setCellValue("hours at pumping Pmax");
            
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("AREA_1");
            dataRow.createCell(1).setCellValue(1000.0);
            dataRow.createCell(2).setCellValue("daily");
            dataRow.createCell(3).setCellValue(500.0);
            dataRow.createCell(4).setCellValue(24.0);
            dataRow.createCell(5).setCellValue("annual");
            dataRow.createCell(6).setCellValue(300.0);
            dataRow.createCell(7).setCellValue(12.0);
            
            Files.createDirectories(testExcelPath.getParent());
            try (var fos = Files.newOutputStream(testExcelPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createExcelFileWithDailyPumpingTimestep() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2021");
            
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            headerRow.createCell(1).setCellValue("Reservoir Capacity [MWh]");
            headerRow.createCell(2).setCellValue("Generating Pmax - timestep (daily/annual)");
            headerRow.createCell(3).setCellValue("Generating Pmax [MW]");
            headerRow.createCell(4).setCellValue("hours at generating Pmax");
            headerRow.createCell(5).setCellValue("Pumping Pmax - timestep (daily/annual)");
            headerRow.createCell(6).setCellValue("Pumping Pmax [MW]");
            headerRow.createCell(7).setCellValue("hours at pumping Pmax");
            
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("AREA_1");
            dataRow.createCell(1).setCellValue(1000.0);
            dataRow.createCell(2).setCellValue("annual");
            dataRow.createCell(3).setCellValue(500.0);
            dataRow.createCell(4).setCellValue(24.0);
            dataRow.createCell(5).setCellValue("daily");
            dataRow.createCell(6).setCellValue(300.0);
            dataRow.createCell(7).setCellValue(12.0);
            
            Files.createDirectories(testExcelPath.getParent());
            try (var fos = Files.newOutputStream(testExcelPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createExcelFileWithBothDailyTimesteps() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2021");
            
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            headerRow.createCell(1).setCellValue("Reservoir Capacity [MWh]");
            headerRow.createCell(2).setCellValue("Generating Pmax - timestep (daily/annual)");
            headerRow.createCell(3).setCellValue("Generating Pmax [MW]");
            headerRow.createCell(4).setCellValue("hours at generating Pmax");
            headerRow.createCell(5).setCellValue("Pumping Pmax - timestep (daily/annual)");
            headerRow.createCell(6).setCellValue("Pumping Pmax [MW]");
            headerRow.createCell(7).setCellValue("hours at pumping Pmax");
            
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("AREA_1");
            dataRow.createCell(1).setCellValue(1000.0);
            dataRow.createCell(2).setCellValue("daily");
            dataRow.createCell(3).setCellValue(500.0);
            dataRow.createCell(4).setCellValue(24.0);
            dataRow.createCell(5).setCellValue("daily");
            dataRow.createCell(6).setCellValue(300.0);
            dataRow.createCell(7).setCellValue(12.0);
            
            Files.createDirectories(testExcelPath.getParent());
            try (var fos = Files.newOutputStream(testExcelPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createExcelFileWithDailyGeneratingTimestepAtPath(Path excelPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2021");
            
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            headerRow.createCell(1).setCellValue("Reservoir Capacity [MWh]");
            headerRow.createCell(2).setCellValue("Generating Pmax - timestep (daily/annual)");
            headerRow.createCell(3).setCellValue("Generating Pmax [MW]");
            headerRow.createCell(4).setCellValue("hours at generating Pmax");
            headerRow.createCell(5).setCellValue("Pumping Pmax - timestep (daily/annual)");
            headerRow.createCell(6).setCellValue("Pumping Pmax [MW]");
            headerRow.createCell(7).setCellValue("hours at pumping Pmax");
            
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("AREA_1");
            dataRow.createCell(1).setCellValue(1000.0);
            dataRow.createCell(2).setCellValue("daily");
            dataRow.createCell(3).setCellValue(500.0);
            dataRow.createCell(4).setCellValue(24.0);
            dataRow.createCell(5).setCellValue("annual");
            dataRow.createCell(6).setCellValue(300.0);
            dataRow.createCell(7).setCellValue(12.0);
            
            Files.createDirectories(excelPath.getParent());
            try (var fos = Files.newOutputStream(excelPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createExcelFileWithDailyPumpingTimestepAtPath(Path excelPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2021");
            
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            headerRow.createCell(1).setCellValue("Reservoir Capacity [MWh]");
            headerRow.createCell(2).setCellValue("Generating Pmax - timestep (daily/annual)");
            headerRow.createCell(3).setCellValue("Generating Pmax [MW]");
            headerRow.createCell(4).setCellValue("hours at generating Pmax");
            headerRow.createCell(5).setCellValue("Pumping Pmax - timestep (daily/annual)");
            headerRow.createCell(6).setCellValue("Pumping Pmax [MW]");
            headerRow.createCell(7).setCellValue("hours at pumping Pmax");
            
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("AREA_1");
            dataRow.createCell(1).setCellValue(1000.0);
            dataRow.createCell(2).setCellValue("annual");
            dataRow.createCell(3).setCellValue(500.0);
            dataRow.createCell(4).setCellValue(24.0);
            dataRow.createCell(5).setCellValue("daily");
            dataRow.createCell(6).setCellValue(300.0);
            dataRow.createCell(7).setCellValue(12.0);
            
            Files.createDirectories(excelPath.getParent());
            try (var fos = Files.newOutputStream(excelPath)) {
                workbook.write(fos);
            }
        }
    }

    private void createExcelFileWithBothDailyTimestepsAtPath(Path excelPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2021");
            
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Node");
            headerRow.createCell(1).setCellValue("Reservoir Capacity [MWh]");
            headerRow.createCell(2).setCellValue("Generating Pmax - timestep (daily/annual)");
            headerRow.createCell(3).setCellValue("Generating Pmax [MW]");
            headerRow.createCell(4).setCellValue("hours at generating Pmax");
            headerRow.createCell(5).setCellValue("Pumping Pmax - timestep (daily/annual)");
            headerRow.createCell(6).setCellValue("Pumping Pmax [MW]");
            headerRow.createCell(7).setCellValue("hours at pumping Pmax");
            
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("AREA_1");
            dataRow.createCell(1).setCellValue(1000.0);
            dataRow.createCell(2).setCellValue("daily");
            dataRow.createCell(3).setCellValue(500.0);
            dataRow.createCell(4).setCellValue(24.0);
            dataRow.createCell(5).setCellValue("daily");
            dataRow.createCell(6).setCellValue(300.0);
            dataRow.createCell(7).setCellValue(12.0);
            
            Files.createDirectories(excelPath.getParent());
            try (var fos = Files.newOutputStream(excelPath)) {
                workbook.write(fos);
            }
        }
    }
}
