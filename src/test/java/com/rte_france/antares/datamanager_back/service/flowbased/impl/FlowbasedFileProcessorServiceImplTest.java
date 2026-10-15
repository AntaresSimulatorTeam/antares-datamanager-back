package com.rte_france.antares.datamanager_back.service.flowbased.impl;

import com.rte_france.antares.datamanager_back.dto.FlowbasedLinkCapacityType;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedAreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedConstraintsRestrictionEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkListEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.Utils;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    private AreaRepository areaRepository;

    @Mock
    private UserService userService;

    @Mock
    private FlowbasedConstraintsRestrictionsFileProcessorService restrictionsFileProcessorService;

    @InjectMocks
    private FlowbasedFileProcessorServiceImpl flowbasedFileProcessorService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(restrictionsFileProcessorService.readRestrictions(any(Path.class), anyString()))
                .thenReturn(new FlowbasedConstraintsRestrictionsFileProcessorService.RestrictionsData(List.of(), List.of()));
        when(areaRepository.findAllByStudyId(1, TrajectoryType.AREA.name()))
                .thenReturn(List.of(AreaEntity.builder().name("FR").build()));
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

        assertTrue(exception.getMessage().contains("Required files are missing: {0} in Flowbased trajectory {1}"));
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
                any(String.class), any(String.class), any(String.class), isNull(), isNull()))
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
                any(String.class), any(String.class), any(String.class), isNull(), isNull()))
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
            assertEquals(List.of("FR"), result.getFlowbasedAreas().stream().map(FlowbasedAreaEntity::getArea).toList());
            assertTrue(result.getFlowbasedConstraintsRestrictions().isEmpty());
            assertTrue(result.getFlowbasedLinkLists().isEmpty());
            verify(trajectoryRepository, times(2)).save(any(TrajectoryEntity.class));
        }
    }

    @Test
    void processFlowbasedFiles_shouldAttachRestrictionsToSavedTrajectory(@TempDir Path tempDir) throws IOException {
        createAllRequiredFilesWithContent(tempDir);
        var constraint = FlowbasedConstraintsRestrictionEntity.builder()
                .name("restriction_ahc").enabled(true).listLinks("listLinks1").build();
        var link = FlowbasedLinkListEntity.builder()
                .listName("listLinks1").link("ch-fr").poids(-1.0).build();
        when(restrictionsFileProcessorService.readRestrictions(tempDir, "2026-2027"))
                .thenReturn(new FlowbasedConstraintsRestrictionsFileProcessorService.RestrictionsData(
                        List.of(constraint), List.of(link)));
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = flowbasedFileProcessorService.processFlowbasedFiles(tempDir, "trajectory", 1, "2026-2027");

        assertEquals(List.of(constraint), result.getFlowbasedConstraintsRestrictions());
        assertEquals(List.of(link), result.getFlowbasedLinkLists());
        assertSame(result, constraint.getTrajectory());
        assertSame(result, link.getTrajectory());
    }

    @Test
    void processFlowbasedFiles_shouldNotPersistWhenRestrictionsAreInvalid(@TempDir Path tempDir) throws IOException {
        createAllRequiredFilesWithContent(tempDir);
        var invalidRestrictions = BusinessException.builder()
                .message("Missing listLinks1 in restrictions workbook")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
        when(restrictionsFileProcessorService.readRestrictions(tempDir, "2026-2027"))
                .thenThrow(invalidRestrictions);

        assertSame(invalidRestrictions, assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.processFlowbasedFiles(tempDir, "trajectory", 1, "2026-2027")));
        verify(trajectoryRepository, never()).save(any(TrajectoryEntity.class));
    }

    @Test
    void processFlowbasedFiles_shouldRejectAreasNotFoundInStudy(@TempDir Path tempDir) throws Exception {
        createAllRequiredFilesWithContent(tempDir);
        createAreasNecessarySheet(tempDir.resolve("areas_necessary.xlsx"), "NL");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.processFlowbasedFiles(tempDir, "trajectory", 1, "2025-2026"));

        assertEquals("Mandatory Areas {0} in Flowbased trajectory are missing in AREA trajectory", exception.getMessage());
        assertEquals(List.of("NL"), exception.getErrorMessageArguments());
        verify(trajectoryRepository, never()).save(any(TrajectoryEntity.class));
    }

    @Test
    void processFlowbasedFiles_shouldAcceptHvdcNodesInEitherDirectionIgnoringCase(@TempDir Path directory) throws IOException {
        createAllRequiredFilesWithContent(directory);
        replaceNodes(directory, "z_fr_de", "Z_dE_fR", "z_Nos_nl");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), 1))
                .thenReturn(List.of(TrajectoryEntity.builder().linkEntities(List.of(
                        LinkEntity.builder().name("DE-FR").hvdcMwDirect(10.0).hvdcMwIndirect(20.0).build(),
                        LinkEntity.builder().name("nOs-NL").hvdcMwDirect(-10.0).hvdcMwIndirect(-20.0).build()
                )).build()));
        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = flowbasedFileProcessorService.processFlowbasedFiles(directory, "trajectory", 1, "2025-2026");

        assertEquals(List.of("z_fr_de", "Z_dE_fR", "z_Nos_nl"),
                result.getFlowbasedVirtualNodes().stream().map(FlowbasedVirtualNodesEntity::getName).toList());
        verify(trajectoryRepository, times(1)).findByTypeAndStudyId(TrajectoryType.LINK.name(), 1);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,10", "10,NULL", "0,10", "10,0", "NULL,NULL", "0,0", "-0.0,10"}, nullValues = "NULL")
    void processFlowbasedFiles_shouldRejectInvalidHvdcCapacity(Double direct, Double indirect, @TempDir Path directory) throws IOException {
        createAllRequiredFilesWithContent(directory);
        replaceNodes(directory, "z_fr_de");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), 1))
                .thenReturn(List.of(TrajectoryEntity.builder().linkEntities(List.of(
                        LinkEntity.builder().name("FR-DE").hvdcMwDirect(direct).hvdcMwIndirect(indirect).build()
                )).build()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.processFlowbasedFiles(directory, "trajectory", 1, "2025-2026"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals(List.of("z_fr_de"), exception.getErrorMessageArguments());
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void processFlowbasedFiles_shouldNotCombineCapacitiesFromDifferentLinks(@TempDir Path directory) throws IOException {
        createAllRequiredFilesWithContent(directory);
        replaceNodes(directory, "z_fr_de");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), 1))
                .thenReturn(List.of(TrajectoryEntity.builder().linkEntities(List.of(
                        LinkEntity.builder().name("FR-DE").hvdcMwDirect(10.0).hvdcMwIndirect(0.0).build(),
                        LinkEntity.builder().name("DE-FR").hvdcMwDirect(0.0).hvdcMwIndirect(10.0).build()
                )).build()));

        assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.processFlowbasedFiles(directory, "trajectory", 1, "2025-2026"));

        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void processFlowbasedFiles_shouldReportMissingPairsOnceInNodeOrder(@TempDir Path directory) throws IOException {
        createAllRequiredFilesWithContent(directory);
        replaceNodes(directory, "z_Nos_nl", "z_fr_de", "z_Nos_nl");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), 1))
                .thenReturn(List.of(TrajectoryEntity.builder().linkEntities(List.of(
                        LinkEntity.builder().name("FR-CH").hvdcMwDirect(10.0).hvdcMwIndirect(20.0).build()
                )).build()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.processFlowbasedFiles(directory, "trajectory", 1, "2025-2026"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals(List.of("z_Nos_nl, z_fr_de"), exception.getErrorMessageArguments());
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void processFlowbasedFiles_shouldRejectHvdcNodeWithoutLinkedTrajectory(@TempDir Path directory) throws IOException {
        createAllRequiredFilesWithContent(directory);
        replaceNodes(directory, "z_fr_de");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), 1)).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.processFlowbasedFiles(directory, "trajectory", 1, "2025-2026"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals(List.of("z_fr_de"), exception.getErrorMessageArguments());
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void processFlowbasedFiles_shouldNotUseLinksAssociatedWithAnotherStudy(@TempDir Path directory) throws IOException {
        createAllRequiredFilesWithContent(directory);
        replaceNodes(directory, "z_fr_de");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), 2))
                .thenReturn(List.of(TrajectoryEntity.builder().linkEntities(List.of(
                        LinkEntity.builder().name("FR-DE").hvdcMwDirect(10.0).hvdcMwIndirect(20.0).build()
                )).build()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.processFlowbasedFiles(directory, "trajectory", 1, "2025-2026"));

        assertEquals(List.of("z_fr_de"), exception.getErrorMessageArguments());
        verify(trajectoryRepository).findByTypeAndStudyId(TrajectoryType.LINK.name(), 1);
        verify(trajectoryRepository, never()).findByTypeAndStudyId(TrajectoryType.LINK.name(), 2);
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void processFlowbasedFiles_shouldLeaveOtherNodeFormatsUnchangedWithoutLinkedTrajectory(@TempDir Path directory) throws IOException {
        createAllRequiredFilesWithContent(directory);
        List<String> names = List.of("Node1", "FR", "z_fr", "z_fr_de_extra", "z__de", "z_fr_");
        replaceNodes(directory, names.toArray(String[]::new));
        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = flowbasedFileProcessorService.processFlowbasedFiles(directory, "trajectory", 1, "2025-2026");

        assertEquals(names, result.getFlowbasedVirtualNodes().stream().map(FlowbasedVirtualNodesEntity::getName).toList());
        verify(trajectoryRepository, never()).findByTypeAndStudyId(anyString(), anyInt());
    }

    @Test
    void processFlowbasedFiles_shouldVersionChangesInRestrictionSeries(@TempDir Path directory) throws IOException {
        createAllRequiredFilesWithContent(directory);
        Path series = Files.createDirectories(directory.resolve("restriction_ahc/ts")).resolve("series.txt");
        Files.writeString(series, "first content");
        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var original = flowbasedFileProcessorService.processFlowbasedFiles(directory, "trajectory", 1, "2025-2026");
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                "trajectory", TrajectoryType.FLOWBASED.name(), "2025-2026", null, null))
                .thenReturn(Optional.of(original));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> flowbasedFileProcessorService.processFlowbasedFiles(directory, "trajectory", 1, "2025-2026"));
        assertEquals(HttpStatus.CONFLICT, duplicate.getHttpStatus());

        Files.writeString(series, "updated content");
        var updated = flowbasedFileProcessorService.processFlowbasedFiles(directory, "trajectory", 1, "2025-2026");

        assertEquals(2, updated.getVersion());
        assertNotEquals(original.getChecksum(), updated.getChecksum());
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

    private void replaceNodes(Path directory, String... names) throws IOException {
        Path file = directory.resolve("Flowbased_nodes_links.xlsx");
        try (var input = Files.newInputStream(file);
             var workbook = new XSSFWorkbook(input)) {
            workbook.removeSheetAt(workbook.getSheetIndex("nodes"));
            var sheet = workbook.createSheet("nodes");
            for (int i = 0; i < names.length; i++) {
                sheet.createRow(i).createCell(0).setCellValue(names[i]);
            }
            try (var output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
    }

    private void createAllRequiredFiles(@TempDir Path tempDir) throws IOException {
        createAllRequiredFilesExcept(tempDir);
    }

    private void createAllRequiredFilesWithContent(Path tempDir) throws IOException {
        String[] requiredFiles = {
                "Flowbased_nodes_links.xlsx",
                "IdTypDays.csv",
                "second_member.txt",
                "weight.txt",
                "ts.txt",
                "areas_necessary.xlsx"
        };

        for (String fileName : requiredFiles) {
            Path filePath = tempDir.resolve(fileName);
            if (fileName.equals("IdTypDays.csv")) {
                Files.writeString(filePath, "clustering;id_type_day;class_day\nCluster1;1;Day1\n");
            } else if (fileName.equals("Flowbased_nodes_links.xlsx")) {
                createLinksSheet(filePath);
            } else if (fileName.equals("areas_necessary.xlsx")) {
                createAreasNecessarySheet(filePath, "FR");
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
                "weight.txt",
                "ts.txt",
                "areas_necessary.xlsx"
        };

        var excludeSet = java.util.Set.of(excludeFiles);
        for (String fileName : requiredFiles) {
            if (!excludeSet.contains(fileName)) {
                Files.createFile(tempDir.resolve(fileName));
            }
        }
    }

    private void createAreasNecessarySheet(Path excelPath, String... areas) throws IOException {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("areas");
            sheet.createRow(0).createCell(0).setCellValue("areas");
            for (int i = 0; i < areas.length; i++) {
                sheet.createRow(i + 1).createCell(0).setCellValue(areas[i]);
            }
            try (var outputStream = new FileOutputStream(excelPath.toFile())) {
                workbook.write(outputStream);
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

    // ============================================================================
    // getIntegerCellValue Tests
    // ============================================================================

    @Test
    void getIntegerCellValue_returnsIntegerWhenNumericCell() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue(42);

            Integer result = flowbasedFileProcessorService.getIntegerCellValue(row, 0);
            assertEquals(42, result);
        }
    }

    @Test
    void getIntegerCellValue_returnsNullWhenCellIsNull() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            // Don't create any cells - cell at index 0 will be null

            Integer result = flowbasedFileProcessorService.getIntegerCellValue(row, 0);
            assertNull(result);
        }
    }

    @Test
    void getIntegerCellValue_returnsNullWhenStringValueIsInfinite() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("infinite");

            Integer result = flowbasedFileProcessorService.getIntegerCellValue(row, 0);
            assertNull(result);
        }
    }

    @Test
    void getIntegerCellValue_returnsNullWhenStringValueIsInfiniteUpperCase() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("INFINITE");

            Integer result = flowbasedFileProcessorService.getIntegerCellValue(row, 0);
            assertNull(result);
        }
    }

    @Test
    void getIntegerCellValue_returnsNullWhenStringValueIsInfiniteMixedCase() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("InFiNiTe");

            Integer result = flowbasedFileProcessorService.getIntegerCellValue(row, 0);
            assertNull(result);
        }
    }

    @Test
    void getIntegerCellValue_returnsIntegerWhenStringValueIsNumeric() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("123");

            Integer result = flowbasedFileProcessorService.getIntegerCellValue(row, 0);
            assertEquals(123, result);
        }
    }

    @Test
    void getIntegerCellValue_returnsIntegerWhenStringValueIsNumericWithSpaces() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("  456  ");

            Integer result = flowbasedFileProcessorService.getIntegerCellValue(row, 0);
            assertEquals(456, result);
        }
    }

    @Test
    void getIntegerCellValue_throwsBusinessExceptionWhenStringValueIsNotNumericAndNotInfinite() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("invalid_value");

            assertThrows(BusinessException.class, () ->
                flowbasedFileProcessorService.getIntegerCellValue(row, 0));
        }
    }

    @Test
    void getIntegerCellValue_throwsBusinessExceptionWhenStringValueIsDecimal() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("123.45");

            assertThrows(BusinessException.class, () ->
                flowbasedFileProcessorService.getIntegerCellValue(row, 0));
        }
    }

    @Test
    void getIntegerCellValue_throwsBusinessExceptionWhenCellTypeBool() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue(true);

            assertThrows(BusinessException.class, () ->
                flowbasedFileProcessorService.getIntegerCellValue(row, 0));
        }
    }

    @Test
    void getIntegerCellValue_throwsBusinessExceptionWhenStringValueIsEmpty() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("");

            assertThrows(BusinessException.class, () ->
                flowbasedFileProcessorService.getIntegerCellValue(row, 0));
        }
    }

    @Test
    void getIntegerCellValue_handlesNegativeNumbers() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("-99");

            Integer result = flowbasedFileProcessorService.getIntegerCellValue(row, 0);
            assertEquals(-99, result);
        }
    }

    @Test
    void getIntegerCellValue_handlesZero() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("0");

            Integer result = flowbasedFileProcessorService.getIntegerCellValue(row, 0);
            assertEquals(0, result);
        }
    }

    // ============================================================================
    // getIntegerCellValueWithType Tests
    // ============================================================================

    @Test
    void getIntegerCellValueWithType_returnsEnabledWhenNumericCell() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue(42);

            IntegerCellValue result = flowbasedFileProcessorService.getIntegerCellValueWithType(row, 0);
            assertEquals(42, result.getValue());
            assertEquals(FlowbasedLinkCapacityType.ENABLED, result.getType());
        }
    }

    @Test
    void getIntegerCellValueWithType_returnsDisabledWhenCellIsNull() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);

            IntegerCellValue result = flowbasedFileProcessorService.getIntegerCellValueWithType(row, 0);
            assertNull(result.getValue());
            assertEquals(FlowbasedLinkCapacityType.DISABLED, result.getType());
        }
    }

    @Test
    void getIntegerCellValueWithType_returnsInfiniteWhenStringValueIsInfinite() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("infinite");

            IntegerCellValue result = flowbasedFileProcessorService.getIntegerCellValueWithType(row, 0);
            assertNull(result.getValue());
            assertEquals(FlowbasedLinkCapacityType.INFINITE, result.getType());
        }
    }

    @Test
    void getIntegerCellValueWithType_returnsInfiniteWhenStringValueIsInfiniteUpperCase() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("INFINITE");

            IntegerCellValue result = flowbasedFileProcessorService.getIntegerCellValueWithType(row, 0);
            assertNull(result.getValue());
            assertEquals(FlowbasedLinkCapacityType.INFINITE, result.getType());
        }
    }

    @Test
    void getIntegerCellValueWithType_returnsEnabledWhenStringValueIsNumeric() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("123");

            IntegerCellValue result = flowbasedFileProcessorService.getIntegerCellValueWithType(row, 0);
            assertEquals(123, result.getValue());
            assertEquals(FlowbasedLinkCapacityType.ENABLED, result.getType());
        }
    }

    // ============================================================================
    // determineOverallType Tests
    // ============================================================================

    @Test
    void determineOverallType_returnsEnabled_whenAllValuesAreEnabled() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);

            for (int i = 0; i < 8; i++) {
                row.createCell(i).setCellValue(100 * (i + 1));
            }

            IntegerCellValue[] values = new IntegerCellValue[8];
            for (int i = 0; i < 8; i++) {
                values[i] = flowbasedFileProcessorService.getIntegerCellValueWithType(row, i);
            }

            FlowbasedLinkCapacityType result = flowbasedFileProcessorService.determineOverallType(
                    values[0], values[1], values[2], values[3],
                    values[4], values[5], values[6], values[7]);

            assertEquals(FlowbasedLinkCapacityType.ENABLED, result);
        }
    }

    @Test
    void determineOverallType_returnsInfinite_whenAllValuesAreInfinite() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);

            for (int i = 0; i < 8; i++) {
                row.createCell(i).setCellValue("infinite");
            }

            IntegerCellValue[] values = new IntegerCellValue[8];
            for (int i = 0; i < 8; i++) {
                values[i] = flowbasedFileProcessorService.getIntegerCellValueWithType(row, i);
            }

            FlowbasedLinkCapacityType result = flowbasedFileProcessorService.determineOverallType(
                    values[0], values[1], values[2], values[3],
                    values[4], values[5], values[6], values[7]);

            assertEquals(FlowbasedLinkCapacityType.INFINITE, result);
        }
    }

    @Test
    void determineOverallType_throwsException_whenMixedInfiniteAndNumeric() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);

            row.createCell(0).setCellValue("infinite");
            row.createCell(1).setCellValue(200);
            row.createCell(2).setCellValue(300);
            row.createCell(3).setCellValue(400);
            row.createCell(4).setCellValue(500);
            row.createCell(5).setCellValue(600);
            row.createCell(6).setCellValue(700);
            row.createCell(7).setCellValue(800);

            IntegerCellValue[] values = new IntegerCellValue[8];
            for (int i = 0; i < 8; i++) {
                values[i] = flowbasedFileProcessorService.getIntegerCellValueWithType(row, i);
            }

            assertThrows(BusinessException.class, () ->
                flowbasedFileProcessorService.determineOverallType(
                    values[0], values[1], values[2], values[3],
                    values[4], values[5], values[6], values[7]));
        }
    }

    @Test
    void determineOverallType_returnsDisabled_whenSomeValuesAreDisabled() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);

            row.createCell(0).setCellValue(100);
            row.createCell(1).setCellValue(200);
            row.createCell(2).setCellValue(300);
            row.createCell(3).setCellValue(400);
            // Cells 4-7 not created (null/DISABLED)

            IntegerCellValue[] values = new IntegerCellValue[8];
            for (int i = 0; i < 8; i++) {
                values[i] = flowbasedFileProcessorService.getIntegerCellValueWithType(row, i);
            }

            FlowbasedLinkCapacityType result = flowbasedFileProcessorService.determineOverallType(
                    values[0], values[1], values[2], values[3],
                    values[4], values[5], values[6], values[7]);

            assertEquals(FlowbasedLinkCapacityType.DISABLED, result);
        }
    }

    @Test
    void determineOverallType_throwsException_whenMixedInfiniteAndDisabled() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet();
            var row = sheet.createRow(0);

            row.createCell(0).setCellValue("infinite");
            row.createCell(1).setCellValue(200);
            row.createCell(2).setCellValue(300);
            row.createCell(3).setCellValue(400);
            // Cells 4-7 not created (DISABLED)

            IntegerCellValue[] values = new IntegerCellValue[8];
            for (int i = 0; i < 8; i++) {
                values[i] = flowbasedFileProcessorService.getIntegerCellValueWithType(row, i);
            }

            assertThrows(BusinessException.class, () ->
                flowbasedFileProcessorService.determineOverallType(
                    values[0], values[1], values[2], values[3],
                    values[4], values[5], values[6], values[7]));
        }
    }
}
