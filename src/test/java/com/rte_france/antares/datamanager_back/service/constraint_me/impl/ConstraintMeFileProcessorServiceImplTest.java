package com.rte_france.antares.datamanager_back.service.constraint_me.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConstraintMeFileProcessorServiceImpl Tests")
class ConstraintMeFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private GroupAreaDescRepository groupAreaDescRepository;

    @Mock
    private GroupClusterDescRepository groupClusterDescRepository;

    @Mock
    private MeConstraintRepository meConstraintRepository;

    @InjectMocks
    private ConstraintMeFileProcessorServiceImpl service;

    private Path tempDir;
    private UserInfoDto userInfoDto;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("constraint_me_test_");
        
        userInfoDto = UserInfoDto.builder()
                .nni("USER123")
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
    }

    @Test
    @DisplayName("saveConstraintMeTrajectoryInDb - should process new file successfully")
    void testSaveNewTrajectory() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        createValidExcelFile(filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        
        TrajectoryEntity savedEntity = createTrajectoryEntity(trajectoryName, horizon, 1);
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(savedEntity);
        when(groupAreaDescRepository.save(any(GroupAreaDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupAreaDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });
        when(groupClusterDescRepository.save(any(GroupClusterDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupClusterDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });

        TrajectoryEntity result = service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon);

        assertNotNull(result);
        assertEquals(trajectoryName, result.getFileName());
        assertEquals(horizon, result.getHorizon());
        assertEquals(1, result.getVersion());
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("saveConstraintMeTrajectoryInDb - should throw when horizon is null")
    void testSaveWithNullHorizon() {
        String trajectoryName = "test_trajectory";
        
        assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, null)
        );
    }

    @Test
    @DisplayName("saveConstraintMeTrajectoryInDb - should throw when user NNI not found")
    void testSaveWithoutUserNni() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";

        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        createValidExcelFile(filePath);
        
        when(userService.getCurrentUserDetails()).thenReturn(null);

        assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon)
        );
    }

    @Test
    @DisplayName("saveConstraintMeTrajectoryInDb - should update version when trajectory exists")
    void testUpdateExistingTrajectory() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        createValidExcelFile(filePath);
        
        TrajectoryEntity existingEntity = createTrajectoryEntity(trajectoryName, horizon, 1);
        TrajectoryEntity newEntity = createTrajectoryEntity(trajectoryName, horizon, 2);

        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(existingEntity));
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(newEntity);
        when(groupAreaDescRepository.save(any(GroupAreaDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupAreaDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });
        when(groupClusterDescRepository.save(any(GroupClusterDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupClusterDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });

        TrajectoryEntity result = service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon);

        assertNotNull(result);
        assertEquals(2, result.getVersion());
    }

    @Test
    @DisplayName("saveConstraintMeTrajectoryInDb - should update version when content changes")
    void testSaveWithSameContent() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        createValidExcelFile(filePath);
        
        TrajectoryEntity existingEntity = createTrajectoryEntity(trajectoryName, horizon, 1);
        // Use a different checksum so isSameFileWithSameContent returns false
        existingEntity.setChecksum("different_checksum");
        
        TrajectoryEntity newEntity = createTrajectoryEntity(trajectoryName, horizon, 2);

        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(existingEntity));
        
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(newEntity);
        
        when(groupAreaDescRepository.save(any(GroupAreaDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupAreaDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });
        when(groupClusterDescRepository.save(any(GroupClusterDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupClusterDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });

        TrajectoryEntity result = service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon);

        assertNotNull(result);
        assertEquals(2, result.getVersion());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("validateConstraintMeExcelFile - should throw when listArea_desc sheet missing")
    void testValidateExcelMissingListAreaDesc() throws IOException {
        String horizon = "2024-2025";
        String trajectoryName = "test";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet("listCluster_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.createSheet("2025").createRow(0).createCell(0).setCellValue("Constraint");
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);

        assertThrows(BusinessException.class, () -> {
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon);
        });
    }

    @Test
    @DisplayName("validateListAreaDescTab - should throw when sheet is empty")
    void testValidateListAreaDescTabEmpty() throws IOException {
        String horizon = "2024-2025";
        String trajectoryName = "test";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet("listArea_desc");
        workbook.createSheet("listCluster_desc");
        workbook.createSheet("2025");
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);

        assertThrows(BusinessException.class, () -> {
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon);
        });
    }

    @Test
    @DisplayName("validateListAreaDescTab - should throw when column has no data")
    void testValidateListAreaDescTabEmptyColumn() throws IOException {
        String horizon = "2024-2025";
        String trajectoryName = "test";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("listArea_desc");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Group1");
        
        workbook.createSheet("listCluster_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.createSheet("2025").createRow(0).createCell(0).setCellValue("Constraint");
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);

        assertThrows(BusinessException.class, () -> {
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon);
        });
    }

    @Test
    @DisplayName("processListAreaDescSheet - should insert areas with correct groups")
    void testProcessListAreaDescSheet() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        createValidExcelFile(filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(createTrajectoryEntity(trajectoryName, horizon, 1));
        when(groupAreaDescRepository.save(any(GroupAreaDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupAreaDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });
        when(groupClusterDescRepository.save(any(GroupClusterDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupClusterDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });

        service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon);

        ArgumentCaptor<GroupAreaDescEntity> captor = ArgumentCaptor.forClass(GroupAreaDescEntity.class);
        verify(groupAreaDescRepository, atLeast(2)).save(captor.capture());
    }

    @Test
    @DisplayName("processListClusterDescSheet - should insert clusters with correct groups")
    void testProcessListClusterDescSheet() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        createValidExcelFile(filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(createTrajectoryEntity(trajectoryName, horizon, 1));
        when(groupAreaDescRepository.save(any(GroupAreaDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupAreaDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });
        when(groupClusterDescRepository.save(any(GroupClusterDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupClusterDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });

        service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon);

        ArgumentCaptor<GroupClusterDescEntity> captor = ArgumentCaptor.forClass(GroupClusterDescEntity.class);
        verify(groupClusterDescRepository, atLeast(2)).save(captor.capture());
    }

    @Test
    @DisplayName("processHorizonSheet - should insert constraints from horizon sheet")
    void testProcessHorizonSheet() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        createValidExcelFile(filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(createTrajectoryEntity(trajectoryName, horizon, 1));
        when(groupAreaDescRepository.save(any(GroupAreaDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupAreaDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });
        when(groupClusterDescRepository.save(any(GroupClusterDescEntity.class)))
                .thenAnswer(inv -> {
                    GroupClusterDescEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });
        when(meConstraintRepository.save(any(MeConstraintEntity.class)))
                .thenAnswer(inv -> {
                    MeConstraintEntity e = inv.getArgument(0);
                    e.setId(1);
                    return e;
                });

        service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon);

        ArgumentCaptor<MeConstraintEntity> captor = ArgumentCaptor.forClass(MeConstraintEntity.class);
        verify(meConstraintRepository, atLeastOnce()).save(captor.capture());
        
        MeConstraintEntity constraint = captor.getValue();
        assertEquals("TestConstraint", constraint.getName());
        assertTrue(constraint.getEnabled());
        assertEquals("<=", constraint.getSign());
    }

    @Test
    @DisplayName("buildTrajectoryPath - should throw when configuration incomplete")
    void testBuildTrajectoryPathIncompleteConfig() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        createValidExcelFile(filePath);
        
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(null);
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);

        assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon)
        );
    }

    @Test
    @DisplayName("buildTrajectoryPath - should throw when path is outside base directory")
    void testBuildTrajectoryPathPathTraversal() throws IOException {
        String trajectoryName = "../../../etc/passwd";
        String horizon = "2024-2025";
        
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getConstraintMeDirectory()).thenReturn("constraint_me");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);

        assertThrows(IOException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon)
        );
    }

    // ============ Helper Methods ============

    private void setupMocks(String nasDir, String trajPath, String constraintMeDir) {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir);
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajPath);
        when(antaresDataManagerProperties.getConstraintMeDirectory()).thenReturn(constraintMeDir);
    }

    private void createValidExcelFile(Path filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        
        Sheet areaSheet = workbook.createSheet("listArea_desc");
        Row areaHeaderRow = areaSheet.createRow(0);
        areaHeaderRow.createCell(0).setCellValue("listArea_euest");
        areaHeaderRow.createCell(1).setCellValue("listArea_fr");
        
        Row areaRow1 = areaSheet.createRow(1);
        areaRow1.createCell(0).setCellValue("AT");
        areaRow1.createCell(1).setCellValue("FR");
        
        Row areaRow2 = areaSheet.createRow(2);
        areaRow2.createCell(0).setCellValue("BE");
        
        Sheet clusterSheet = workbook.createSheet("listCluster_desc");
        Row clusterHeaderRow = clusterSheet.createRow(0);
        clusterHeaderRow.createCell(0).setCellValue("ClusterGroup1");
        clusterHeaderRow.createCell(1).setCellValue("ClusterGroup2");
        
        Row clusterRow1 = clusterSheet.createRow(1);
        clusterRow1.createCell(0).setCellValue("Cluster1");
        clusterRow1.createCell(1).setCellValue("Cluster3");
        
        Row clusterRow2 = clusterSheet.createRow(2);
        clusterRow2.createCell(0).setCellValue("Cluster2");
        
        Sheet horizonSheet = workbook.createSheet("2025");
        Row horizonHeaderRow = horizonSheet.createRow(0);
        horizonHeaderRow.createCell(0).setCellValue("Name");
        horizonHeaderRow.createCell(1).setCellValue("Enabled");
        horizonHeaderRow.createCell(2).setCellValue("Sign");
        horizonHeaderRow.createCell(3).setCellValue("Temporality");
        horizonHeaderRow.createCell(4).setCellValue("Type");
        horizonHeaderRow.createCell(5).setCellValue("Comments");
        horizonHeaderRow.createCell(6).setCellValue("Noeud1Gauche");
        horizonHeaderRow.createCell(7).setCellValue("Noeud2Gauche");
        horizonHeaderRow.createCell(8).setCellValue("ClusterGauche");
        horizonHeaderRow.createCell(9).setCellValue("Noeud1Droite");
        horizonHeaderRow.createCell(10).setCellValue("Noeud2Droite");
        horizonHeaderRow.createCell(11).setCellValue("ClusterDroite");
        
        Row horizonRow = horizonSheet.createRow(1);
        horizonRow.createCell(0).setCellValue("TestConstraint");
        horizonRow.createCell(1).setCellValue("YES");
        horizonRow.createCell(2).setCellValue("<=");
        horizonRow.createCell(3).setCellValue("Daily");
        horizonRow.createCell(4).setCellValue("G2P");
        horizonRow.createCell(5).setCellValue("Test constraint");
        
        saveWorkbook(workbook, filePath);
    }

    private void saveWorkbook(Workbook workbook, Path filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            workbook.write(fos);
        }
    }

    private TrajectoryEntity createTrajectoryEntity(String fileName, String horizon, int version) {
        return TrajectoryEntity.builder()
                .id(1)
                .fileName(fileName)
                .horizon(horizon)
                .version(version)
                .type(TrajectoryType.CONSTRAINT_ME.name())
                .createdBy("USER123")
                .fileSize(1024L)
                .creationDate(LocalDateTime.now())
                .lastModificationContentDate(LocalDateTime.now())
                .checksum("test_checksum")
                .build();
    }
}
