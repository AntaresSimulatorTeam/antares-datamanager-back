package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.misc.impl.InstalledMiscFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class InstalledMiscFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private TrajectoryServiceImpl trajectoryService;

    @InjectMocks
    private InstalledMiscFileProcessorServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(trajectoryRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        when(userService.getCurrentUserDetails())
                .thenReturn(null);
    }

    // ======================================================
    // Helpers
    // ======================================================

    private Path createWorkbook(List<Object[]> rows) throws Exception {

        Path file = Files.createTempFile(tempDir, "installedMisc_", ".xlsx");

        try (Workbook wb = new XSSFWorkbook()) {

            Sheet s = wb.createSheet("InstalledMisc");

            Row header = s.createRow(0);
            header.createCell(0).setCellValue("ToUse");
            header.createCell(1).setCellValue("Area");
            header.createCell(2).setCellValue("Group");
            header.createCell(3).setCellValue("Cluster");
            header.createCell(4).setCellValue("Category");
            header.createCell(5).setCellValue(2030);

            int rowIndex = 1;

            for (Object[] values : rows) {
                Row r = s.createRow(rowIndex++);

                for (int i = 0; i < values.length; i++) {
                    Cell c = r.createCell(i);

                    Object v = values[i];
                    if (v instanceof Boolean b) c.setCellValue(b);
                    else if (v instanceof Number n) c.setCellValue(n.doubleValue());
                    else if (v != null) c.setCellValue(v.toString());
                }
            }

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        when(trajectoryService.getTrajectoryFilePath(any(), anyString(), any()))
                .thenReturn(file);

        when(trajectoryRepository
                .findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                        anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        return file;
    }

    // ======================================================
    // Tests
    // ======================================================

    @Test
    void shouldThrowExceptionWhenTrajectoryNameInvalid() {

        assertThatThrownBy(() ->
                service.processInstalledMiscFile("badName", "2029-2030", 1, "FR"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldImportOnlyToUseTrueRows() throws Exception {

        createWorkbook(List.of(
                new Object[]{1, "FR", "g1", "c1", "cat", 100},
                new Object[]{0, "FR", "g2", "c2", "cat", 200}
        ));

        TrajectoryEntity result =
                service.processInstalledMiscFile("installedMisc_test",
                        "2029-2030", 1, "FR");

        assertThat(result.getMiscClusterCapacityEntities())
                .hasSize(2);
    }

    @Test
    void shouldFilterByArea() throws Exception {

        createWorkbook(List.of(
                new Object[]{1, "FR", "g1", "c1", "cat", 100},
                new Object[]{1, "DE", "g2", "c2", "cat", 200}
        ));

        TrajectoryEntity result =
                service.processInstalledMiscFile("installedMisc_test",
                        "2029-2030", 1, "FR");

        assertThat(result.getMiscClusterCapacityEntities())
                .hasSize(1);

        assertThat(result.getMiscClusterCapacityEntities()
                .getFirst()
                .getArea()).isEqualTo("FR");
    }

    @Test
    void shouldAcceptBooleanToUseCell() throws Exception {

        createWorkbook(List.of(
                new Object[]{true, "FR", "g", "c", "cat", 100},
                new Object[]{1, "DE", "g2", "c2", "cat", 200}
        ));

        TrajectoryEntity result =
                service.processInstalledMiscFile("installedMisc_test",
                        "2029-2030", 1, "FR");

        assertThat(result.getMiscClusterCapacityEntities())
                .hasSize(1);
    }


    @Test
    void shouldIncrementVersionWhenChecksumChanges() throws Exception {

        TrajectoryEntity existing = new TrajectoryEntity();
        existing.setChecksum("OLD");
        existing.setVersion(2);

        when(trajectoryRepository
                .findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                        anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(existing));

        createWorkbook(List.of(
                new Object[]{1, "FR", "g", "c", "cat", 100},
                new Object[]{1, "DE", "g2", "c2", "cat", 200}
        ));

        TrajectoryEntity result =
                service.processInstalledMiscFile(
                        "installedMisc_test",
                        "2029-2030",
                        1,
                        "FR");

        assertThat(result.getVersion()).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenHorizonColumnMissing() throws Exception {

        Path file = Files.createTempFile(tempDir, "installedMisc_", ".xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("InstalledMisc");

            Row header = s.createRow(0);
            header.createCell(0).setCellValue("ToUse");
            header.createCell(1).setCellValue("Area");
            header.createCell(2).setCellValue("Group");
            header.createCell(3).setCellValue("Cluster");
            header.createCell(4).setCellValue("Category");
            header.createCell(5).setCellValue(2025);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        when(trajectoryService.getTrajectoryFilePath(any(), anyString(), any()))
                .thenReturn(file);

        assertThatThrownBy(() ->
                service.processInstalledMiscFile(
                        "installedMisc_test",
                        "2029-2030",
                        1,
                        "FR"))
                .isInstanceOf(BusinessException.class);
    }
}

