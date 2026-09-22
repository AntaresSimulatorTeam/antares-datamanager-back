package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.HydroAllocationMeRepository;
import com.rte_france.antares.datamanager_back.repository.HydroParametersMeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HydroParametersMeFileProcessorServiceImpl Tests")
class HydroParametersMeFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private HydroParametersMeRepository hydroParametersMeRepository;

    @Mock
    private HydroAllocationMeRepository hydroAllocationMeRepository;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private UserService userService;

    @Mock
    private PathSecurityUtil pathSecurityUtil;

    @InjectMocks
    private HydroParametersMeFileProcessorServiceImpl service;

    private Path tempDir;
    private String trajectoryName;
    private String horizon;
    private String horizonYear;
    private Integer trajectoryId;
    private UserInfoDto userInfoDto;

    @BeforeEach
    void setUp() throws IOException {
        trajectoryName = "test_trajectory";
        horizon = "2024-2025";
        horizonYear = "2025";
        trajectoryId = 1;
        userInfoDto = UserInfoDto.builder().nni("USER123").build();

        // Create temp directory
        tempDir = Files.createTempDirectory("hydro_test_");

        when(userService.getCurrentUserDetails()).thenReturn(userInfoDto);
        when(pathSecurityUtil.resolveSafePath(any(java.util.function.Function.class))).thenReturn(tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    // ==================== File Validation Tests ====================

    @Test
    @DisplayName("Should throw exception when param_hydro_ME.xlsx is missing")
    void testMissingParamHydroFile() throws IOException {
        createHydroAllocationMeFile();

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertNotNull(exception);
    }

    @Test
    @DisplayName("Should throw exception when hydroAllocation_ME.xlsx is missing")
    void testMissingHydroAllocationFile() throws IOException {
        createParamHydroMeFile();

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertNotNull(exception);
    }

    // ==================== Horizon Validation Tests ====================

    @Test
    @DisplayName("Should throw exception when horizon sheet is missing in param_hydro_ME.xlsx")
    void testMissingHorizonSheetInParamHydro() throws IOException {
        createParamHydroMeFileWithHorizon("2026");
        createHydroAllocationMeFile();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().contains("Missing horizon"));
    }

    // ==================== Node Column Validation Tests ====================

    @Test
    @DisplayName("Should throw exception when Node column is empty in param_hydro_ME.xlsx")
    void testEmptyNodeColumn() throws IOException {
        createParamHydroMeFileWithEmptyNode();
        createHydroAllocationMeFile();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().toLowerCase().contains("node"));
    }

    @Test
    @DisplayName("Should throw exception when Node name exceeds 60 characters")
    void testNodeNameExceeds60Chars() throws IOException {
        createParamHydroMeFileWithLongNodeName();
        createHydroAllocationMeFile();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().contains("60"));
    }

    // ==================== Numeric Column Validation Tests ====================

    @Test
    @DisplayName("Should throw exception when numeric column contains non-numeric value")
    void testInvalidNumericColumn() throws IOException {
        createParamHydroMeFileWithInvalidNumeric();
        createHydroAllocationMeFile();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().contains("numeric"));
    }

    // ==================== Boolean Column Validation Tests ====================

    @Test
    @DisplayName("Should throw exception when boolean column contains invalid value")
    void testInvalidBooleanColumn() throws IOException {
        createParamHydroMeFileWithInvalidBoolean();
        createHydroAllocationMeFile();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().contains("boolean"));
    }

    // ==================== Load Column Validation Tests ====================

    @Test
    @DisplayName("Should throw exception when load column is empty in hydroAllocation_ME.xlsx")
    void testEmptyLoadColumn() throws IOException {
        createParamHydroMeFile();
        createHydroAllocationMeFileWithEmptyLoad();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().toLowerCase().contains("load"));
    }

    // ==================== Allocation Coefficient Validation Tests ====================

    @Test
    @DisplayName("Should throw exception when allocation coefficient is not numeric")
    void testNodeAllocationNotNumeric() throws IOException {
        createParamHydroMeFile();
        createHydroAllocationMeFileWithNonNumericCoefficient();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().contains("numeric"));
    }

    // ==================== Successful Processing Tests ====================

    @Test
    @DisplayName("Should successfully process valid files")
    void testSuccessfulProcessing() throws IOException {
        createBothFiles();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });
        
        // Mock valid area and nodes
        com.rte_france.antares.datamanager_back.repository.model.AreaEntity area = 
            com.rte_france.antares.datamanager_back.repository.model.AreaEntity.builder()
                .id(1)
                .name("area_1")
                .build();
        when(areaRepository.findAllByStudyId(1, TrajectoryType.AREA.toString())).thenReturn(List.of(area));
        
        HydroParametersMeEntity hydroParam1 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_1")
                .build();
        HydroParametersMeEntity hydroParam2 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_2")
                .build();
        when(hydroParametersMeRepository.findByTrajectoryId(trajectoryId)).thenReturn(List.of(hydroParam1, hydroParam2));
        
        when(hydroParametersMeRepository.saveAll(any())).thenReturn(List.of());
        when(hydroAllocationMeRepository.saveAll(any())).thenReturn(List.of());

        TrajectoryEntity result = service.processHydroParametersMeDirectory(trajectoryName, horizon, 1);

        assertNotNull(result);
        assertEquals(trajectoryId, result.getId());
        assertEquals(trajectoryName, result.getFileName());
        assertEquals(horizon, result.getHorizon());
        assertEquals(1, result.getVersion());
    }

    // ==================== Business Rule Validation Tests ====================

    @Test
    @DisplayName("Should throw exception when area in load column is missing from AREA and HYDRO_ME Param (RG1)")
    void testMissingAreaInLoadColumn() throws IOException {
        createParamHydroMeFile();
        createHydroAllocationMeFile();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });
        
        // Mock with empty area and only one node (area_1 not found)
        when(areaRepository.findAllByStudyId(1, TrajectoryType.AREA.toString())).thenReturn(List.of());
        
        HydroParametersMeEntity hydroParam1 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_1")
                .build();
        HydroParametersMeEntity hydroParam2 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_2")
                .build();
        when(hydroParametersMeRepository.findByTrajectoryId(trajectoryId)).thenReturn(List.of(hydroParam1, hydroParam2));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().contains("Missing Areas/nodes"));
        assertTrue(exception.getMessage().contains("in AREA or HYDRO_ME Param trajectory"));
    }

    @Test
    @DisplayName("RG1: Should throw exception when area from load column doesn't exist in AREA or HYDRO_ME Param")
    void testRG1_MissingAreaNotInAreaOrHydroMeParam() throws IOException {
        createParamHydroMeFile();
        createHydroAllocationMeFile();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });
        
        // Mock with NO areas so area_1 from load column is missing
        when(areaRepository.findAllByStudyId(1, TrajectoryType.AREA.toString())).thenReturn(List.of());
        
        // Valid nodes in HYDRO_ME Param
        HydroParametersMeEntity hydroParam1 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_1")
                .build();
        HydroParametersMeEntity hydroParam2 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_2")
                .build();
        when(hydroParametersMeRepository.findByTrajectoryId(trajectoryId)).thenReturn(List.of(hydroParam1, hydroParam2));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().contains("Missing Areas/nodes"));
    }

    @Test
    @DisplayName("RG1: Should succeed when all areas/nodes from load column exist in AREA or HYDRO_ME Param")
    void testRG1_SuccessWhenAreasValid() throws IOException {
        createParamHydroMeFile();
        createHydroAllocationMeFile();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });
        
        // Mock with valid area area_1
        com.rte_france.antares.datamanager_back.repository.model.AreaEntity area = 
            com.rte_france.antares.datamanager_back.repository.model.AreaEntity.builder()
                .id(1)
                .name("area_1")
                .build();
        when(areaRepository.findAllByStudyId(1, TrajectoryType.AREA.toString())).thenReturn(List.of(area));
        
        // Valid nodes in HYDRO_ME Param
        HydroParametersMeEntity hydroParam1 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_1")
                .build();
        HydroParametersMeEntity hydroParam2 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_2")
                .build();
        when(hydroParametersMeRepository.findByTrajectoryId(trajectoryId)).thenReturn(List.of(hydroParam1, hydroParam2));

        // Should not throw exception
        service.processHydroParametersMeDirectory(trajectoryName, horizon, 1);
    }

    @Test
    @DisplayName("Should throw exception when node in header is missing from HYDRO_ME Param (RG2)")
    void testMissingNodeInHeader() throws IOException {
        createParamHydroMeFile();
        createHydroAllocationMeFile();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });
        
        // Mock with valid area but missing node_2
        com.rte_france.antares.datamanager_back.repository.model.AreaEntity area = 
            com.rte_france.antares.datamanager_back.repository.model.AreaEntity.builder()
                .id(1)
                .name("area_1")
                .build();
        when(areaRepository.findAllByStudyId(1, TrajectoryType.AREA.toString())).thenReturn(List.of(area));
        
        // Only include node_1, not node_2
        HydroParametersMeEntity hydroParam1 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_1")
                .build();
        when(hydroParametersMeRepository.findByTrajectoryId(trajectoryId)).thenReturn(List.of(hydroParam1));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().contains("Missing Nodes"));
        assertTrue(exception.getMessage().contains("in HYDRO_ME Param trajectory"));
    }

    @Test
    @DisplayName("RG2: Should throw exception when multiple nodes from header are missing from HYDRO_ME Param")
    void testRG2_MultipleNodesNotInHydroMeParam() throws IOException {
        createParamHydroMeFileWithThreeNodes();
        createHydroAllocationMeFileWithThreeNodes();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });
        
        // Mock with valid area
        com.rte_france.antares.datamanager_back.repository.model.AreaEntity area = 
            com.rte_france.antares.datamanager_back.repository.model.AreaEntity.builder()
                .id(1)
                .name("area_1")
                .build();
        when(areaRepository.findAllByStudyId(1, TrajectoryType.AREA.toString())).thenReturn(List.of(area));
        
        // Only include node_1, missing node_2 and node_3
        HydroParametersMeEntity hydroParam1 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_1")
                .build();
        when(hydroParametersMeRepository.findByTrajectoryId(trajectoryId)).thenReturn(List.of(hydroParam1));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroParametersMeDirectory(trajectoryName, horizon, 1)
        );

        assertTrue(exception.getMessage().contains("Missing Nodes"), 
                "Exception message: " + exception.getMessage());
    }

    @Test
    @DisplayName("RG2: Should succeed when all nodes from header exist in HYDRO_ME Param")
    void testRG2_SuccessWhenNodesValid() throws IOException {
        createParamHydroMeFile();
        createHydroAllocationMeFile();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
            TrajectoryEntity entity = invocation.getArgument(0);
            entity.setId(trajectoryId);
            return entity;
        });
        
        // Mock with valid area
        com.rte_france.antares.datamanager_back.repository.model.AreaEntity area = 
            com.rte_france.antares.datamanager_back.repository.model.AreaEntity.builder()
                .id(1)
                .name("area_1")
                .build();
        when(areaRepository.findAllByStudyId(1, TrajectoryType.AREA.toString())).thenReturn(List.of(area));
        
        // All nodes present in HYDRO_ME Param
        HydroParametersMeEntity hydroParam1 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_1")
                .build();
        HydroParametersMeEntity hydroParam2 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_2")
                .build();
        when(hydroParametersMeRepository.findByTrajectoryId(trajectoryId)).thenReturn(List.of(hydroParam1, hydroParam2));

        // Should not throw exception
        service.processHydroParametersMeDirectory(trajectoryName, horizon, 1);
    }

    // ==================== Test Helper Methods ====================

    private void createBothFiles() throws IOException {
        createParamHydroMeFile();
        createHydroAllocationMeFile();
    }

    private void createParamHydroMeFile() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);

        var headerRow = sheet.createRow(0);
        String[] headers = {"Node", "inter.monthly.correlation", "intra.daily.modulation", "inter.daily.breakdown",
                "inter.monthly.breakdown", "initialize.reservoir.date", "leeway.low", "leeway.up", "pumping.efficiency",
                "reservoir management", "follow.load", "use.heuristic", "use.water", "hard.bounds", "use.leeway", "power.to.level"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("node_1");
        dataRow.createCell(1).setCellValue(0.5);
        dataRow.createCell(2).setCellValue(0.6);
        dataRow.createCell(3).setCellValue(0.7);
        dataRow.createCell(4).setCellValue(0.8);
        dataRow.createCell(5).setCellValue(10);
        dataRow.createCell(6).setCellValue(0.2);
        dataRow.createCell(7).setCellValue(0.3);
        dataRow.createCell(8).setCellValue(0.9);
        dataRow.createCell(9).setCellValue(true);
        dataRow.createCell(10).setCellValue(false);
        dataRow.createCell(11).setCellValue(true);
        dataRow.createCell(12).setCellValue(false);
        dataRow.createCell(13).setCellValue(true);
        dataRow.createCell(14).setCellValue(false);
        dataRow.createCell(15).setCellValue(true);

        var dataRow2 = sheet.createRow(2);
        dataRow2.createCell(0).setCellValue("node_2");
        dataRow2.createCell(1).setCellValue(0.5);
        dataRow2.createCell(2).setCellValue(0.6);
        dataRow2.createCell(3).setCellValue(0.7);
        dataRow2.createCell(4).setCellValue(0.8);
        dataRow2.createCell(5).setCellValue(10);
        dataRow2.createCell(6).setCellValue(0.2);
        dataRow2.createCell(7).setCellValue(0.3);
        dataRow2.createCell(8).setCellValue(0.9);
        dataRow2.createCell(9).setCellValue(true);
        dataRow2.createCell(10).setCellValue(false);
        dataRow2.createCell(11).setCellValue(true);
        dataRow2.createCell(12).setCellValue(false);
        dataRow2.createCell(13).setCellValue(true);
        dataRow2.createCell(14).setCellValue(false);
        dataRow2.createCell(15).setCellValue(true);

        try (var fos = new FileOutputStream(tempDir.resolve("param_hydro_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createParamHydroMeFileWithThreeNodes() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);

        var headerRow = sheet.createRow(0);
        String[] headers = {"Node", "inter.monthly.correlation", "intra.daily.modulation", "inter.daily.breakdown",
                "inter.monthly.breakdown", "initialize.reservoir.date", "leeway.low", "leeway.up", "pumping.efficiency",
                "reservoir management", "follow.load", "use.heuristic", "use.water", "hard.bounds", "use.leeway", "power.to.level"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // Create 3 data rows for node_1, node_2, node_3
        for (int row = 1; row <= 3; row++) {
            var dataRow = sheet.createRow(row);
            dataRow.createCell(0).setCellValue("node_" + row);
            dataRow.createCell(1).setCellValue(0.5);
            dataRow.createCell(2).setCellValue(0.6);
            dataRow.createCell(3).setCellValue(0.7);
            dataRow.createCell(4).setCellValue(0.8);
            dataRow.createCell(5).setCellValue(10);
            dataRow.createCell(6).setCellValue(0.2);
            dataRow.createCell(7).setCellValue(0.3);
            dataRow.createCell(8).setCellValue(0.9);
            dataRow.createCell(9).setCellValue(true);
            dataRow.createCell(10).setCellValue(false);
            dataRow.createCell(11).setCellValue(true);
            dataRow.createCell(12).setCellValue(false);
            dataRow.createCell(13).setCellValue(true);
            dataRow.createCell(14).setCellValue(false);
            dataRow.createCell(15).setCellValue(true);
        }

        try (var fos = new FileOutputStream(tempDir.resolve("param_hydro_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createParamHydroMeFileWithHorizon(String sheetName) throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(sheetName);
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Node");

        try (var fos = new FileOutputStream(tempDir.resolve("param_hydro_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createParamHydroMeFileWithEmptyNode() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Node");
        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("");
        dataRow.createCell(1).setCellValue(0.5);

        try (var fos = new FileOutputStream(tempDir.resolve("param_hydro_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createParamHydroMeFileWithLongNodeName() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Node");
        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("a".repeat(61));
        dataRow.createCell(1).setCellValue(0.5);

        try (var fos = new FileOutputStream(tempDir.resolve("param_hydro_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createParamHydroMeFileWithInvalidNumeric() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Node");
        headerRow.createCell(3).setCellValue("inter.daily.breakdown");
        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("node_1");
        dataRow.createCell(3).setCellValue("not_a_number");

        try (var fos = new FileOutputStream(tempDir.resolve("param_hydro_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createParamHydroMeFileWithInvalidBoolean() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Node");
        headerRow.createCell(9).setCellValue("reservoir management");
        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("node_1");
        dataRow.createCell(9).setCellValue("maybe");

        try (var fos = new FileOutputStream(tempDir.resolve("param_hydro_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createHydroAllocationMeFile() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("load");
        headerRow.createCell(1).setCellValue("node_1");
        headerRow.createCell(2).setCellValue("node_2");

        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("area_1");
        dataRow.createCell(1).setCellValue(0.5);
        dataRow.createCell(2).setCellValue(0.3);

        try (var fos = new FileOutputStream(tempDir.resolve("hydroAllocation_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createHydroAllocationMeFileWithAreaNotInDB() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("load");
        headerRow.createCell(1).setCellValue("node_1");
        headerRow.createCell(2).setCellValue("node_2");

        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("area_1");
        dataRow.createCell(1).setCellValue(0.5);
        dataRow.createCell(2).setCellValue(0.3);

        var dataRow2 = sheet.createRow(2);
        dataRow2.createCell(0).setCellValue("area_2");
        dataRow2.createCell(1).setCellValue(0.4);
        dataRow2.createCell(2).setCellValue(0.2);

        try (var fos = new FileOutputStream(tempDir.resolve("hydroAllocation_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createHydroAllocationMeFileWithThreeNodes() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("load");
        headerRow.createCell(1).setCellValue("node_1");
        headerRow.createCell(2).setCellValue("node_2");
        headerRow.createCell(3).setCellValue("node_3");

        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("area_1");
        dataRow.createCell(1).setCellValue(0.5);
        dataRow.createCell(2).setCellValue(0.3);
        dataRow.createCell(3).setCellValue(0.2);

        try (var fos = new FileOutputStream(tempDir.resolve("hydroAllocation_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createHydroAllocationMeFileWithEmptyLoad() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("load");
        headerRow.createCell(1).setCellValue("node_1");
        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("");
        dataRow.createCell(1).setCellValue(0.5);

        try (var fos = new FileOutputStream(tempDir.resolve("hydroAllocation_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createHydroAllocationMeFileWithNonNumericCoefficient() throws IOException {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet(horizonYear);
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("load");
        headerRow.createCell(1).setCellValue("node_1");
        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("area_1");
        dataRow.createCell(1).setCellValue("not_a_number");

        try (var fos = new FileOutputStream(tempDir.resolve("hydroAllocation_ME.xlsx").toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void setupValidAreaAndNodeMocks() {
        // Mock AreaRepository to return valid area
        com.rte_france.antares.datamanager_back.repository.model.AreaEntity area = 
            com.rte_france.antares.datamanager_back.repository.model.AreaEntity.builder()
                .id(1)
                .name("area_1")
                .build();
        when(areaRepository.findAll()).thenReturn(List.of(area));
        
        // Mock HydroParametersMeRepository to return valid nodes
        HydroParametersMeEntity hydroParam1 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_1")
                .build();
        HydroParametersMeEntity hydroParam2 = HydroParametersMeEntity.builder()
                .trajectoryId(trajectoryId)
                .node("node_2")
                .build();
        when(hydroParametersMeRepository.findByTrajectoryId(trajectoryId)).thenReturn(List.of(hydroParam1, hydroParam2));
    }
}
