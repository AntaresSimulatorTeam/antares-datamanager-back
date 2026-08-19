package com.rte_france.antares.datamanager_back.service.flowbased.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkWeightEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.Utils;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlowbasedFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private FlowbasedFileProcessorServiceImpl flowbasedFileProcessorService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void validateRequiredFiles_shouldNotThrowWhenAllFilesPresent(@TempDir Path tempDir) throws IOException {
        createAllRequiredFiles(tempDir);

        assertDoesNotThrow(() -> flowbasedFileProcessorService.validateRequiredFiles(tempDir));
    }
    
    @Test
    void validateRequiredFiles_shouldThrowExceptionWhenMultipleFilesAreMissing(@TempDir Path tempDir) throws IOException {
        createAllRequiredFilesExcept(tempDir, "Flowbased_nodes_links.xlsx", "IdTypDays.csv");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.validateRequiredFiles(tempDir));

        assertTrue(exception.getMessage().contains("Required files are missing"));
        assertTrue(exception.getMessage().contains("Flowbased_nodes_links.xlsx"));
        assertTrue(exception.getMessage().contains("IdTypDays.csv"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void validateRequiredFiles_shouldThrowExceptionWhenAllFilesAreMissing(@TempDir Path tempDir) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.validateRequiredFiles(tempDir));

        assertTrue(exception.getMessage().contains("Required files are missing"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void processFlowbasedFiles_shouldThrowExceptionWhenRequiredFilesAreMissing(@TempDir Path tempDir) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.processFlowbasedFiles(tempDir, "trajectory", 1, "2025-2026"));

        assertTrue(exception.getMessage().contains("Required files are missing"));
    }

    @Test
    void processFlowbasedFiles_shouldThrowExceptionWhenChecksumCalculationFails(@TempDir Path tempDir) throws IOException {
        createAllRequiredFilesWithContent(tempDir);

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.calculateFlowbasedFilesChecksum(tempDir))
                    .thenThrow(new IOException("Checksum calculation error"));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> flowbasedFileProcessorService.processFlowbasedFiles(tempDir, "trajectory", 1, "2025-2026"));

            assertTrue(exception.getMessage().contains("Error processing flowbased files") || 
                      exception.getMessage().contains("Checksum calculation error"));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        }
    }

    @Test
    void processFlowbasedFiles_shouldThrowExceptionWhenDuplicateFileWithSameChecksumExists(@TempDir Path tempDir) throws Exception {
        createAllRequiredFilesWithContent(tempDir);

        LocalDateTime modificationDate = LocalDateTime.now();
        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .checksum("sameChecksum")
                .lastModificationContentDate(modificationDate)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), isNull(), isNull()))
                .thenReturn(Optional.of(existingTrajectory));

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            utilsMock.when(() -> Utils.calculateFlowbasedFilesChecksum(tempDir))
                    .thenReturn("sameChecksum");
            
            var mockFileTime = mock(java.nio.file.attribute.FileTime.class);
            when(mockFileTime.toMillis()).thenReturn(modificationDate.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            filesMock.when(() -> Files.getLastModifiedTime(tempDir))
                    .thenReturn(mockFileTime);

            assertThrows(BusinessException.class,
                    () -> flowbasedFileProcessorService.processFlowbasedFiles(tempDir, "trajectory", 1, "2025-2026"));
        }
    }

    @Test
    void processFlowbasedFiles_shouldSaveNewTrajectoryWhenNoExistingFound(@TempDir Path tempDir) throws Exception {
        createAllRequiredFilesWithContent(tempDir);

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), isNull(), isNull()))
                .thenReturn(Optional.empty());
        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER001").build());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.calculateFlowbasedFilesChecksum(tempDir))
                    .thenReturn("newChecksum");

            TrajectoryEntity result = flowbasedFileProcessorService.processFlowbasedFiles(tempDir, "trajectory", 1, "2025-2026");

            assertNotNull(result);
            assertEquals("trajectory", result.getFileName());
            assertEquals(TrajectoryType.FLOWBASED.name(), result.getType());
            assertEquals(1, result.getVersion());
            verify(trajectoryRepository, times(2)).save(any(TrajectoryEntity.class));
        }
    }

    @Test
    void buildFlowbasedVirtualNodesList_shouldParseValidExcelFile(@TempDir Path tempDir) throws Exception {
        Path excelPath = tempDir.resolve("Flowbased_nodes_links.xlsx");
        createNodesSheet(excelPath, new String[]{"Node1", "Node2", "Node3"});

        List<FlowbasedVirtualNodesEntity> result = flowbasedFileProcessorService.buildFlowbasedVirtualNodesList(tempDir);

        assertEquals(3, result.size());
        assertEquals("Node1", result.get(0).getName());
        assertEquals("Node2", result.get(1).getName());
        assertEquals("Node3", result.get(2).getName());
    }

    @Test
    void buildFlowbasedVirtualNodesList_shouldSkipEmptyRowNames(@TempDir Path tempDir) throws Exception {
        Path excelPath = tempDir.resolve("Flowbased_nodes_links.xlsx");
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("nodes");
            sheet.createRow(1).createCell(0).setCellValue("Node1");
            sheet.createRow(2).createCell(0).setCellValue("");
            sheet.createRow(3).createCell(0).setCellValue("Node2");
            try (var fos = new FileOutputStream(excelPath.toFile())) {
                wb.write(fos);
            }
        }

        List<FlowbasedVirtualNodesEntity> result = flowbasedFileProcessorService.buildFlowbasedVirtualNodesList(tempDir);

        assertEquals(2, result.size());
        assertEquals("Node1", result.get(0).getName());
        assertEquals("Node2", result.get(1).getName());
    }

    @Test
    void buildFlowbasedVirtualNodesList_shouldSkipNullRowNames(@TempDir Path tempDir) throws Exception {
        Path excelPath = tempDir.resolve("Flowbased_nodes_links.xlsx");
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("nodes");
            sheet.createRow(1).createCell(0).setCellValue("Node1");
            sheet.createRow(2); // Null row with no cells
            sheet.createRow(3).createCell(0).setCellValue("Node2");
            try (var fos = new FileOutputStream(excelPath.toFile())) {
                wb.write(fos);
            }
        }

        List<FlowbasedVirtualNodesEntity> result = flowbasedFileProcessorService.buildFlowbasedVirtualNodesList(tempDir);

        assertEquals(2, result.size());
    }

    @Test
    void buildFlowbasedVirtualNodesList_shouldThrowExceptionWhenSheetNotFound(@TempDir Path tempDir) throws Exception {
        Path excelPath = tempDir.resolve("Flowbased_nodes_links.xlsx");
        try (var wb = new XSSFWorkbook();
             var fos = new FileOutputStream(excelPath.toFile())) {
            wb.createSheet("wrong_sheet");
            wb.write(fos);
        }

        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.buildFlowbasedVirtualNodesList(tempDir));

        assertTrue(exception.getMessage().contains("Sheet 'nodes' not found"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void buildFlowbasedVirtualNodesList_shouldThrowExceptionWhenFileNotFound(@TempDir Path tempDir) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.buildFlowbasedVirtualNodesList(tempDir));

        assertTrue(exception.getMessage().contains("Error reading Flowbased_nodes_links.xlsx"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void buildFlowbasedLinkCapacityList_shouldParseValidExcelFile(@TempDir Path tempDir) throws Exception {
        Path excelPath = tempDir.resolve("Flowbased_nodes_links.xlsx");
        createLinksSheet(excelPath);

        List<FlowbasedLinkCapacityEntity> result = flowbasedFileProcessorService.buildFlowbasedLinkCapacityList(tempDir);

        assertEquals(1, result.size());
        FlowbasedLinkCapacityEntity entity = result.get(0);
        assertEquals("Link1", entity.getName());
        assertEquals(100, entity.getWinterHPDirectMW());
        assertEquals(110, entity.getWinterHPIndirectMW());
        assertEquals(120, entity.getWinterHCDirectMW());
        assertEquals(130, entity.getWinterHCIndirectMW());
        assertEquals(140, entity.getSummerHPDirectMW());
        assertEquals(150, entity.getSummerHPIndirectMW());
        assertEquals(160, entity.getSummerHCDirectMW());
        assertEquals(170, entity.getSummerHCIndirectMW());
        assertTrue(entity.getHurdlesCost());
    }

    @Test
    void buildFlowbasedLinkCapacityList_shouldSkipRowsWithEmptyName(@TempDir Path tempDir) throws Exception {
        Path excelPath = tempDir.resolve("Flowbased_nodes_links.xlsx");
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("links");
            createLinksHeader(sheet);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("");
            row.createCell(1).setCellValue(100);
            try (var fos = new FileOutputStream(excelPath.toFile())) {
                wb.write(fos);
            }
        }

        List<FlowbasedLinkCapacityEntity> result = flowbasedFileProcessorService.buildFlowbasedLinkCapacityList(tempDir);

        assertEquals(0, result.size());
    }

    @Test
    void buildFlowbasedLinkCapacityList_shouldHandleNullNumericValues(@TempDir Path tempDir) throws Exception {
        Path excelPath = tempDir.resolve("Flowbased_nodes_links.xlsx");
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("links");
            createLinksHeader(sheet);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Link1");
            row.createCell(1).setBlank(); // null numeric value
            try (var fos = new FileOutputStream(excelPath.toFile())) {
                wb.write(fos);
            }
        }

        List<FlowbasedLinkCapacityEntity> result = flowbasedFileProcessorService.buildFlowbasedLinkCapacityList(tempDir);

        assertEquals(1, result.size());
        assertNull(result.get(0).getWinterHPDirectMW());
    }

    @Test
    void buildFlowbasedLinkCapacityList_shouldThrowExceptionWhenSheetNotFound(@TempDir Path tempDir) throws Exception {
        Path excelPath = tempDir.resolve("Flowbased_nodes_links.xlsx");
        try (var wb = new XSSFWorkbook();
             var fos = new FileOutputStream(excelPath.toFile())) {
            wb.createSheet("wrong_sheet");
            wb.write(fos);
        }

        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.buildFlowbasedLinkCapacityList(tempDir));

        assertTrue(exception.getMessage().contains("Sheet 'links' not found"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void buildFlowbasedLinkCapacityList_shouldThrowExceptionWhenFileNotFound(@TempDir Path tempDir) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.buildFlowbasedLinkCapacityList(tempDir));

        assertTrue(exception.getMessage().contains("Error reading Flowbased_nodes_links.xlsx"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void buildFlowbasedTypeDayList_shouldParseValidCsvFile(@TempDir Path tempDir) throws IOException {
        Path csvPath = tempDir.resolve("IdTypDays.csv");
        Files.writeString(csvPath, "clustering;id_type_day;class_day\nCluster1;1;Day1\nCluster2;2;Day2\n");

        List<FlowbasedTypeDayEntity> result = flowbasedFileProcessorService.buildFlowbasedTypeDayList(tempDir);

        assertEquals(2, result.size());
        assertEquals("Cluster1", result.get(0).getClustering());
        assertEquals(1, result.get(0).getIdTypeDay());
        assertEquals("Day1", result.get(0).getClassDay());
        assertEquals("Cluster2", result.get(1).getClustering());
        assertEquals(2, result.get(1).getIdTypeDay());
        assertEquals("Day2", result.get(1).getClassDay());
    }

    @Test
    void buildFlowbasedTypeDayList_shouldSkipHeaderRow(@TempDir Path tempDir) throws IOException {
        Path csvPath = tempDir.resolve("IdTypDays.csv");
        Files.writeString(csvPath, "clustering;id_type_day;class_day\nCluster1;1;Day1\n");

        List<FlowbasedTypeDayEntity> result = flowbasedFileProcessorService.buildFlowbasedTypeDayList(tempDir);

        assertEquals(1, result.size());
    }

    @Test
    void buildFlowbasedTypeDayList_shouldSkipRowsWithInsufficientColumns(@TempDir Path tempDir) throws IOException {
        Path csvPath = tempDir.resolve("IdTypDays.csv");
        Files.writeString(csvPath, "clustering;id_type_day;class_day\nCluster1;1\nCluster2;2;Day2\n");

        List<FlowbasedTypeDayEntity> result = flowbasedFileProcessorService.buildFlowbasedTypeDayList(tempDir);

        assertEquals(1, result.size());
        assertEquals("Cluster2", result.get(0).getClustering());
    }

    @Test
    void buildFlowbasedTypeDayList_shouldSkipRowsWithInvalidIntegerValue(@TempDir Path tempDir) throws IOException {
        Path csvPath = tempDir.resolve("IdTypDays.csv");
        Files.writeString(csvPath, "clustering;id_type_day;class_day\nCluster1;invalid;Day1\nCluster2;2;Day2\n");

        List<FlowbasedTypeDayEntity> result = flowbasedFileProcessorService.buildFlowbasedTypeDayList(tempDir);

        assertEquals(1, result.size());
        assertEquals("Cluster2", result.get(0).getClustering());
    }

    @Test
    void buildFlowbasedTypeDayList_shouldTrimWhitespace(@TempDir Path tempDir) throws IOException {
        Path csvPath = tempDir.resolve("IdTypDays.csv");
        Files.writeString(csvPath, "clustering;id_type_day;class_day\n  Cluster1  ;  1  ;  Day1  \n");

        List<FlowbasedTypeDayEntity> result = flowbasedFileProcessorService.buildFlowbasedTypeDayList(tempDir);

        assertEquals(1, result.size());
        assertEquals("Cluster1", result.get(0).getClustering());
    }

    @Test
    void buildFlowbasedTypeDayList_shouldThrowExceptionWhenFileNotFound(@TempDir Path tempDir) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.buildFlowbasedTypeDayList(tempDir));

        assertTrue(exception.getMessage().contains("Error reading IdTypDays.csv"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void buildFlowbasedTypeDayList_shouldReturnEmptyListForEmptyCsvFile(@TempDir Path tempDir) throws IOException {
        Path csvPath = tempDir.resolve("IdTypDays.csv");
        Files.writeString(csvPath, "clustering;id_type_day;class_day\n");

        List<FlowbasedTypeDayEntity> result = flowbasedFileProcessorService.buildFlowbasedTypeDayList(tempDir);

        assertEquals(0, result.size());
    }

    private void createAllRequiredFiles(@TempDir Path tempDir) throws IOException {
        createAllRequiredFilesExcept(tempDir);
    }

    private void createAllRequiredFilesWithContent(Path tempDir) throws IOException {
        String[] requiredFiles = {
                "Flowbased_nodes_links.xlsx",
                "IdTypDays.csv",
                "second_member.txt",
                "weight.txt"
        };

        for (String fileName : requiredFiles) {
            Path filePath = tempDir.resolve(fileName);
            if (fileName.equals("IdTypDays.csv")) {
                Files.writeString(filePath, "clustering;id_type_day;class_day\nCluster1;1;Day1\n");
            } else if (fileName.equals("Flowbased_nodes_links.xlsx")) {
                createLinksSheet(filePath);
            } else {
                Files.createFile(filePath);
            }
        }
    }

    private void createAllRequiredFilesExcept(Path tempDir, String... excludeFiles) throws IOException {
        String[] requiredFiles = {
                "Flowbased_nodes_links.xlsx",
                "IdTypDays.csv",
                "second_member.txt",
                "weight.txt"
        };

        var excludeSet = java.util.Set.of(excludeFiles);
        for (String fileName : requiredFiles) {
            if (!excludeSet.contains(fileName)) {
                Files.createFile(tempDir.resolve(fileName));
            }
        }
    }

    private void createNodesSheet(Path excelPath, String[] nodeNames) throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("nodes");
            for (int i = 0; i < nodeNames.length; i++) {
                sheet.createRow(i + 1).createCell(0).setCellValue(nodeNames[i]);
            }
            try (var fos = new FileOutputStream(excelPath.toFile())) {
                wb.write(fos);
            }
        }
    }

    private void createLinksSheet(Path excelPath) throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var nodesSheet = wb.createSheet("nodes");
            nodesSheet.createRow(1).createCell(0).setCellValue("Node1");
            nodesSheet.createRow(2).createCell(0).setCellValue("Node2");

            var linksSheet = wb.createSheet("links");
            createLinksHeader(linksSheet);
            var row = linksSheet.createRow(1);
            row.createCell(0).setCellValue("Link1");
            row.createCell(1).setCellValue(100);
            row.createCell(2).setCellValue(110);
            row.createCell(3).setCellValue(120);
            row.createCell(4).setCellValue(130);
            row.createCell(5).setCellValue(140);
            row.createCell(6).setCellValue(150);
            row.createCell(7).setCellValue(160);
            row.createCell(8).setCellValue(170);
            row.createCell(9).setCellValue(true);
            try (var fos = new FileOutputStream(excelPath.toFile())) {
                wb.write(fos);
            }
        }
    }

    private void createLinksHeader(XSSFSheet sheet) {
        var header = sheet.createRow(0);
        String[] headers = {"name", "winter_HP_direct_MW", "winter_HP_indirect_MW", "winter_HC_direct_MW",
                "winter_HC_indirect_MW", "summer_HP_direct_MW", "summer_HP_indirect_MW", "summer_HC_direct_MW",
                "summer_HC_indirect_MW", "hurdles_cost"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
    }
}

