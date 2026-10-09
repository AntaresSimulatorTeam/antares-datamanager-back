package com.rte_france.antares.datamanager_back.service.constraint_me.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.multi_energy.MultiEnergyCoherenceCheckService;
import com.rte_france.antares.datamanager_back.service.multi_energy.impl.MultiEnergyCoherenceCheckServiceImpl;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConstraintMeFileProcessorServiceImpl Tests")
class ConstraintMeFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock(lenient = true)
    private StudyTrajectoryRepository studyTrajectoryRepository;

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

    @Mock(lenient = true)
    private AreaRepository areaRepository;

    private MultiEnergyCoherenceCheckService multiEnergyCoherenceCheckService;

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
        
        // Mock AreaRepository to return area entities
        List<AreaEntity> mockAreas = Arrays.asList(
                AreaEntity.builder().name("area1").build(),
                AreaEntity.builder().name("area2").build(),
                AreaEntity.builder().name("area3").build(),
                AreaEntity.builder().name("AT").build(),
                AreaEntity.builder().name("BE").build(),
                AreaEntity.builder().name("FR").build(),
                AreaEntity.builder().name("Node1").build()
        );
        when(areaRepository.findAllByStudyId(anyInt(), anyString()))
                .thenReturn(mockAreas);
        
        // Create a spy of the real MultiEnergyCoherenceCheckService implementation
        MultiEnergyCoherenceCheckService realService = new MultiEnergyCoherenceCheckServiceImpl(trajectoryRepository, areaRepository);
        multiEnergyCoherenceCheckService = spy(realService);
        
        // Re-inject the spy into the service after @InjectMocks
        ReflectionTestUtils.setField(service, "multiEnergyCoherenceCheckService", multiEnergyCoherenceCheckService);
        
        // Configure the spy to return AREA_ME nodes (lowercase for validation)
        Set<String> areaMeNodes = new HashSet<>(Arrays.asList("node1", "area1", "area2", "area3"));
        doReturn(areaMeNodes).when(multiEnergyCoherenceCheckService).extractNodesFromAreaMeTrajectory(anyInt());
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
                any(String.class), any(String.class), any(String.class)))
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

        TrajectoryEntity result = service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);

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
            service.saveConstraintMeTrajectoryInDb(trajectoryName, null, 1)
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
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
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
                any(String.class), any(String.class), any(String.class)))
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

        TrajectoryEntity result = service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);

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
                any(String.class), any(String.class), any(String.class)))
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

        TrajectoryEntity result = service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);

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
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);
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
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);
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
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);
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
                any(String.class), any(String.class), any(String.class)))
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

        service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);

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
                any(String.class), any(String.class), any(String.class)))
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

        service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);

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
                any(String.class), any(String.class), any(String.class)))
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

        service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);

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
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
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
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
    }

    @Test
    @DisplayName("saveConstraintMeTrajectoryInDb - should handle MSG_FILE_ALREADY_PROCESSED scenario")
    void testSaveThrowsFileAlreadyProcessed() throws IOException {
        // Testing the scenario when existing trajectory is found
        // The actual checksum comparison depends on static method
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        createValidExcelFile(filePath);
        
        TrajectoryEntity existingEntity = createTrajectoryEntity(trajectoryName, horizon, 1);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(Optional.of(existingEntity));
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(createTrajectoryEntity(trajectoryName, horizon, 2));
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
        
        // Should handle existing trajectory and increment version
        TrajectoryEntity result = service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);
        
        assertNotNull(result);
        // Trajectory is saved once in processExistingTrajectory
        verify(trajectoryRepository, atLeastOnce()).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("processListAreaDescSheet - should throw MSG_AREA_NOT_IN_STUDY when area not in study")
    void testThrowsAreaNotInStudy() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        Sheet areaSheet = workbook.createSheet("listArea_desc");
        Row areaHeaderRow = areaSheet.createRow(0);
        areaHeaderRow.createCell(0).setCellValue("InvalidAreaGroup");
        
        Row areaRow = areaSheet.createRow(1);
        areaRow.createCell(0).setCellValue("InvalidArea");
        
        Sheet clusterSheet = workbook.createSheet("listCluster_desc");
        Row clusterHeaderRow = clusterSheet.createRow(0);
        clusterHeaderRow.createCell(0).setCellValue("ClusterGroup");
        Row clusterRow = clusterSheet.createRow(1);
        clusterRow.createCell(0).setCellValue("Cluster1");
        
        Sheet horizonSheet = workbook.createSheet("2025");
        Row horizonHeaderRow = horizonSheet.createRow(0);
        horizonHeaderRow.createCell(0).setCellValue("Name");
        Row horizonRow = horizonSheet.createRow(1);
        horizonRow.createCell(0).setCellValue("TestConstraint");
        
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(createTrajectoryEntity(trajectoryName, horizon, 1));
        
        BusinessException exception = assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
        
        assertTrue(exception.getMessage().contains("does not exist in study areas"));
    }

    @Test
    @DisplayName("processHorizonSheet - should throw MSG_NODE_MUST_BELONG_AREA_ME when node not in AREA_ME")
    void testThrowsNodeMustBelongAreaMe() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        Sheet areaSheet = workbook.createSheet("listArea_desc");
        Row areaHeaderRow = areaSheet.createRow(0);
        areaHeaderRow.createCell(0).setCellValue("listArea_euest");
        Row areaRow = areaSheet.createRow(1);
        areaRow.createCell(0).setCellValue("AT");
        
        Sheet clusterSheet = workbook.createSheet("listCluster_desc");
        Row clusterHeaderRow = clusterSheet.createRow(0);
        clusterHeaderRow.createCell(0).setCellValue("ClusterGroup1");
        Row clusterRow = clusterSheet.createRow(1);
        clusterRow.createCell(0).setCellValue("Cluster1");
        
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
        horizonRow.createCell(6).setCellValue("InvalidNode");
        
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
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
        
        BusinessException exception = assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
        
        assertTrue(exception.getMessage().contains("must belong to AREA_ME trajectory"));
    }

    @Test
    @DisplayName("processHorizonSheet - should throw MSG_CLUSTER_MUST_BELONG_DESC when cluster not in listCluster_desc")
    void testThrowsClusterMustBelongDesc() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        Sheet areaSheet = workbook.createSheet("listArea_desc");
        Row areaHeaderRow = areaSheet.createRow(0);
        areaHeaderRow.createCell(0).setCellValue("listArea_euest");
        Row areaRow = areaSheet.createRow(1);
        areaRow.createCell(0).setCellValue("AT");
        
        Sheet clusterSheet = workbook.createSheet("listCluster_desc");
        Row clusterHeaderRow = clusterSheet.createRow(0);
        clusterHeaderRow.createCell(0).setCellValue("ClusterGroup1");
        Row clusterRow = clusterSheet.createRow(1);
        clusterRow.createCell(0).setCellValue("Cluster1");
        
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
        horizonRow.createCell(8).setCellValue("InvalidCluster");
        
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
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
        
        BusinessException exception = assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
        
        assertTrue(exception.getMessage().contains("must belong to listCluster_desc tab"));
    }

    @Test
    @DisplayName("processListAreaDescSheet - should handle empty sheet gracefully")
    void testProcessListAreaDescSheetEmpty() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        // Create empty listArea_desc sheet
        workbook.createSheet("listArea_desc");
        workbook.createSheet("listCluster_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.createSheet("2025").createRow(0).createCell(0).setCellValue("Name");
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);

        assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
    }

    @Test
    @DisplayName("processListAreaDescSheet - should handle null sheet")
    void testProcessListAreaDescSheetNull() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        // Don't create listArea_desc sheet at all
        workbook.createSheet("listCluster_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.createSheet("2025").createRow(0).createCell(0).setCellValue("Name");
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);

        assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
    }

    @Test
    @DisplayName("processListClusterDescSheet - should handle empty sheet gracefully")
    void testProcessListClusterDescSheetEmpty() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        workbook.createSheet("listArea_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listArea_desc").createRow(1).createCell(0).setCellValue("AT");
        // Create empty listCluster_desc sheet
        workbook.createSheet("listCluster_desc");
        workbook.createSheet("2025").createRow(0).createCell(0).setCellValue("Name");
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);

        assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
    }

    @Test
    @DisplayName("processHorizonSheet - should handle empty horizon sheet gracefully")
    void testProcessHorizonSheetEmpty() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        workbook.createSheet("listArea_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listArea_desc").createRow(1).createCell(0).setCellValue("AT");
        workbook.createSheet("listCluster_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listCluster_desc").createRow(1).createCell(0).setCellValue("Cluster1");
        // Create empty horizon sheet
        workbook.createSheet("2025");
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);

        assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
    }

    @Test
    @DisplayName("processHorizonSheet - should handle null horizon sheet")
    void testProcessHorizonSheetNull() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        workbook.createSheet("listArea_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listArea_desc").createRow(1).createCell(0).setCellValue("AT");
        workbook.createSheet("listCluster_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listCluster_desc").createRow(1).createCell(0).setCellValue("Cluster1");
        // Don't create horizon sheet
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);

        assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
    }

    @Test
    @DisplayName("processHorizonSheet - should skip empty constraint names")
    void testProcessHorizonSheetSkipsEmptyNames() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        workbook.createSheet("listArea_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listArea_desc").createRow(1).createCell(0).setCellValue("AT");
        workbook.createSheet("listCluster_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listCluster_desc").createRow(1).createCell(0).setCellValue("Cluster1");
        
        Sheet horizonSheet = workbook.createSheet("2025");
        Row headerRow = horizonSheet.createRow(0);
        for (int i = 0; i < 12; i++) {
            headerRow.createCell(i).setCellValue("Header" + i);
        }
        
        // Row with empty name should be skipped
        Row emptyRow = horizonSheet.createRow(1);
        emptyRow.createCell(0).setCellValue("");
        emptyRow.createCell(1).setCellValue("YES");
        
        // Row with valid constraint
        Row validRow = horizonSheet.createRow(2);
        validRow.createCell(0).setCellValue("ValidConstraint");
        validRow.createCell(1).setCellValue("YES");
        
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
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
        
        service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);
        
        // Verify that only one constraint is saved (empty name should be skipped)
        ArgumentCaptor<MeConstraintEntity> captor = ArgumentCaptor.forClass(MeConstraintEntity.class);
        verify(meConstraintRepository, times(1)).save(captor.capture());
    }

    @Test
    @DisplayName("validateConstraintData - should validate noeud1Gauche not empty and valid")
    void testValidateNoeud1GaucheValid() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        workbook.createSheet("listArea_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listArea_desc").createRow(1).createCell(0).setCellValue("AT");
        workbook.createSheet("listCluster_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listCluster_desc").createRow(1).createCell(0).setCellValue("Cluster1");
        
        Sheet horizonSheet = workbook.createSheet("2025");
        Row headerRow = horizonSheet.createRow(0);
        for (int i = 0; i < 12; i++) {
            headerRow.createCell(i).setCellValue("Header" + i);
        }
        
        Row validRow = horizonSheet.createRow(1);
        validRow.createCell(0).setCellValue("ValidConstraint");
        validRow.createCell(1).setCellValue("YES");
        validRow.createCell(6).setCellValue("Node1");  // Valid AREA_ME node
        
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        
        // Mock AREA_ME nodes
        List<AreaEntity> areaMeAreas = new ArrayList<>();
        AreaEntity areaMeNode = AreaEntity.builder().name("Node1").build();
        areaMeAreas.add(areaMeNode);
        
        when(areaRepository.findAllByStudyId(1, TrajectoryType.AREA_ME.name()))
                .thenReturn(areaMeAreas);
        
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
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
        
        service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);
        
        ArgumentCaptor<MeConstraintEntity> captor = ArgumentCaptor.forClass(MeConstraintEntity.class);
        verify(meConstraintRepository).save(captor.capture());
        assertEquals("Node1", captor.getValue().getNoeud1Gauche());
    }

    @Test
    @DisplayName("extractConstraintRowData - should extract all fields with correct values")
    void testExtractConstraintRowDataComplete() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        createValidExcelFile(filePath);  // Use the tested valid structure
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
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
        
        service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);
        
        ArgumentCaptor<MeConstraintEntity> captor = ArgumentCaptor.forClass(MeConstraintEntity.class);
        verify(meConstraintRepository).save(captor.capture());
        
        // Verify that the constraint was created and enabled
        MeConstraintEntity constraint = captor.getValue();
        assertEquals("TestConstraint", constraint.getName());
        assertTrue(constraint.getEnabled());
        assertEquals("<=", constraint.getSign());
        assertEquals("Daily", constraint.getTemporality());
        assertEquals("G2P", constraint.getType());
        assertEquals("Test constraint", constraint.getComments());
    }

    @Test
    @DisplayName("extractConstraintRowData - should handle NO value for enabled field")
    void testExtractConstraintRowDataEnabledNo() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        workbook.createSheet("listArea_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listArea_desc").createRow(1).createCell(0).setCellValue("AT");
        workbook.createSheet("listCluster_desc").createRow(0).createCell(0).setCellValue("Group");
        workbook.getSheet("listCluster_desc").createRow(1).createCell(0).setCellValue("Cluster1");
        
        Sheet horizonSheet = workbook.createSheet("2025");
        Row headerRow = horizonSheet.createRow(0);
        for (int i = 0; i < 12; i++) {
            headerRow.createCell(i).setCellValue("Header" + i);
        }
        
        Row dataRow = horizonSheet.createRow(1);
        dataRow.createCell(0).setCellValue("TestConstraint");
        dataRow.createCell(1).setCellValue("NO");  // Not enabled
        
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
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
        
        service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1);
        
        ArgumentCaptor<MeConstraintEntity> captor = ArgumentCaptor.forClass(MeConstraintEntity.class);
        verify(meConstraintRepository).save(captor.capture());
        
        assertFalse(captor.getValue().getEnabled());
    }

    // ============ Helper Methods ============

    private void setupMocks(String nasDir, String trajPath, String constraintMeDir) {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir);
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajPath);
        when(antaresDataManagerProperties.getConstraintMeDirectory()).thenReturn(constraintMeDir);
        
        // Create mock AREA and AREA_ME trajectories
        List<StudyTrajectoryEntity> mockTrajectories = new ArrayList<>();
        
        // Add AREA trajectories with test area names
        TrajectoryEntity areaTrajectory1 = TrajectoryEntity.builder().fileName("AT").type(TrajectoryType.AREA.name()).build();
        StudyTrajectoryEntity studyTraj1 = new StudyTrajectoryEntity();
        studyTraj1.setTrajectory(areaTrajectory1);
        mockTrajectories.add(studyTraj1);
        
        TrajectoryEntity areaTrajectory2 = TrajectoryEntity.builder().fileName("BE").type(TrajectoryType.AREA.name()).build();
        StudyTrajectoryEntity studyTraj2 = new StudyTrajectoryEntity();
        studyTraj2.setTrajectory(areaTrajectory2);
        mockTrajectories.add(studyTraj2);
        
        TrajectoryEntity areaTrajectory3 = TrajectoryEntity.builder().fileName("FR").type(TrajectoryType.AREA.name()).build();
        StudyTrajectoryEntity studyTraj3 = new StudyTrajectoryEntity();
        studyTraj3.setTrajectory(areaTrajectory3);
        mockTrajectories.add(studyTraj3);
        
        // Add AREA_ME trajectories with test node names (using simple names for testing)
        TrajectoryEntity areaMeTrajectory = TrajectoryEntity.builder().fileName("Node1").type(TrajectoryType.AREA_ME.name()).build();
        StudyTrajectoryEntity studyTrajMe = new StudyTrajectoryEntity();
        studyTrajMe.setTrajectory(areaMeTrajectory);
        mockTrajectories.add(studyTrajMe);
        
        when(studyTrajectoryRepository.findById_ScenarioId(anyInt())).thenReturn(mockTrajectories);
        
        // Mock areaRepository.findAllByStudyId for AREA type
        List<AreaEntity> mockAreas = new ArrayList<>();
        AreaEntity area1 = AreaEntity.builder().name("AT").build();
        AreaEntity area2 = AreaEntity.builder().name("BE").build();
        AreaEntity area3 = AreaEntity.builder().name("FR").build();
        mockAreas.add(area1);
        mockAreas.add(area2);
        mockAreas.add(area3);
        
        when(areaRepository.findAllByStudyId(anyInt(), eq(TrajectoryType.AREA.name())))
                .thenReturn(mockAreas);
    }

    @Test
    @DisplayName("validateConstraintMeExcelFile - should throw MSG_MISSING_TABS when SHEET_LIST_CLUSTER_DESC is missing")
    void testThrowsMissingTabsForMissingClusterDescSheet() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        // Create only listArea_desc and Constraints_ME sheets, missing listCluster_desc
        Sheet areaSheet = workbook.createSheet("listArea_desc");
        Row areaHeaderRow = areaSheet.createRow(0);
        areaHeaderRow.createCell(0).setCellValue("listArea_euest");
        Row areaRow = areaSheet.createRow(1);
        areaRow.createCell(0).setCellValue("AT");
        
        Sheet horizonSheet = workbook.createSheet("2025");
        Row horizonHeaderRow = horizonSheet.createRow(0);
        horizonHeaderRow.createCell(0).setCellValue("Name");
        Row horizonRow = horizonSheet.createRow(1);
        horizonRow.createCell(0).setCellValue("TestConstraint");
        
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        
        BusinessException exception = assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
        
        assertTrue(exception.getMessage().contains("Missing tab"));
        assertTrue(exception.getErrorMessageArguments().contains("listCluster_desc"));
    }

    @Test
    @DisplayName("validateListAreaDescTab - should throw MSG_TAB_EMPTY when SHEET_LIST_AREA_DESC is empty")
    void testThrowsTabEmptyForEmptyAreaDescSheet() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        // Create empty listArea_desc sheet
        Sheet areaSheet = workbook.createSheet("listArea_desc");
        // No rows added - sheet is empty
        
        Sheet clusterSheet = workbook.createSheet("listCluster_desc");
        Row clusterHeaderRow = clusterSheet.createRow(0);
        clusterHeaderRow.createCell(0).setCellValue("ClusterGroup");
        Row clusterRow = clusterSheet.createRow(1);
        clusterRow.createCell(0).setCellValue("Cluster1");
        
        Sheet horizonSheet = workbook.createSheet("2025");
        Row horizonHeaderRow = horizonSheet.createRow(0);
        horizonHeaderRow.createCell(0).setCellValue("Name");
        Row horizonRow = horizonSheet.createRow(1);
        horizonRow.createCell(0).setCellValue("TestConstraint");
        
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        
        BusinessException exception = assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
        
        assertTrue(exception.getMessage().contains("Tab cant be empty"));
        assertTrue(exception.getErrorMessageArguments().contains("listArea_desc"));
    }

    @Test
    @DisplayName("validateListClusterDescTab - should throw MSG_TAB_EMPTY when SHEET_LIST_CLUSTER_DESC is empty")
    void testThrowsTabEmptyForEmptyClusterDescSheet() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        Sheet areaSheet = workbook.createSheet("listArea_desc");
        Row areaHeaderRow = areaSheet.createRow(0);
        areaHeaderRow.createCell(0).setCellValue("listArea_euest");
        Row areaRow = areaSheet.createRow(1);
        areaRow.createCell(0).setCellValue("AT");
        
        // Create empty listCluster_desc sheet
        Sheet clusterSheet = workbook.createSheet("listCluster_desc");
        // No rows added - sheet is empty
        
        Sheet horizonSheet = workbook.createSheet("2025");
        Row horizonHeaderRow = horizonSheet.createRow(0);
        horizonHeaderRow.createCell(0).setCellValue("Name");
        Row horizonRow = horizonSheet.createRow(1);
        horizonRow.createCell(0).setCellValue("TestConstraint");
        
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        
        BusinessException exception = assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
        
        assertTrue(exception.getMessage().contains("Tab cant be empty"));
        assertTrue(exception.getErrorMessageArguments().contains("listCluster_desc"));
    }

    @Test
    @DisplayName("validateHorizonTab - should throw MSG_TAB_EMPTY when horizon sheet is empty")
    void testThrowsTabEmptyForEmptyHorizonSheet() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        
        Path filePath = tempDir.resolve(trajectoryName + ".xlsx");
        Workbook workbook = new XSSFWorkbook();
        
        Sheet areaSheet = workbook.createSheet("listArea_desc");
        Row areaHeaderRow = areaSheet.createRow(0);
        areaHeaderRow.createCell(0).setCellValue("listArea_euest");
        Row areaRow = areaSheet.createRow(1);
        areaRow.createCell(0).setCellValue("AT");
        
        Sheet clusterSheet = workbook.createSheet("listCluster_desc");
        Row clusterHeaderRow = clusterSheet.createRow(0);
        clusterHeaderRow.createCell(0).setCellValue("ClusterGroup");
        Row clusterRow = clusterSheet.createRow(1);
        clusterRow.createCell(0).setCellValue("Cluster1");
        
        // Create empty horizon sheet (2025)
        Sheet horizonSheet = workbook.createSheet("2025");
        // No rows added - sheet is empty
        
        saveWorkbook(workbook, filePath);
        
        setupMocks(tempDir.toString(), "", "");
        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        
        BusinessException exception = assertThrows(BusinessException.class, () -> 
            service.saveConstraintMeTrajectoryInDb(trajectoryName, horizon, 1)
        );
        
        assertTrue(exception.getMessage().contains("Tab cant be empty"));
        assertTrue(exception.getErrorMessageArguments().contains("2025"));
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
