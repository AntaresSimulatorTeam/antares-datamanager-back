package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicCo2Entity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicEnerContentEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.TrajectoryService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalEconomicServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ThermalEconomicServiceImplTest {

    @InjectMocks
    private ThermalEconomicServiceImpl thermalEconomicService;

    @Mock
    private UserService userService;

    @Mock
    private TrajectoryRepository trajectoryRepository;


    @Test
    void processThermalEconomicParameterFile_shouldCreateNewTrajectoryWhenNoExistingTrajectory() throws Exception {
        Path temp = Files.createTempFile("trajectory_", ".xlsx");
        try {
            List<ThermalEconomicCo2Entity> co2Entities = List.of(new ThermalEconomicCo2Entity());
            List<ThermalEconomicEnerContentEntity> enerContentEntities = List.of(new ThermalEconomicEnerContentEntity());

            when(userService.getCurrentUserDetails()).thenReturn(null);
            when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TrajectoryEntity result = thermalEconomicService.processThermalEconomicParameterFile(
                    temp, "2023-2024", co2Entities, enerContentEntities, TrajectoryType.THERMAL_ECONOMIC_PARAMETER);

            assertNotNull(result);
            assertEquals("THERMAL_ECONOMIC_PARAMETER", result.getType());
            assertEquals(1, result.getThermalEconomicCo2s().size());
            assertEquals(1, result.getThermalEconomicEnerContents().size());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void processThermalEconomicParameterFile_shouldReuseExistingTrajectoryWhenChecksumMatches() throws Exception {
        Path temp = Files.createTempFile("trajectory_", ".xlsx");
        try {
            List<ThermalEconomicCo2Entity> co2Entities = List.of(new ThermalEconomicCo2Entity());
            List<ThermalEconomicEnerContentEntity> enerContentEntities = List.of(new ThermalEconomicEnerContentEntity());
            TrajectoryEntity existingTrajectory = new TrajectoryEntity();
            existingTrajectory.setVersion(1);

            when(userService.getCurrentUserDetails()).thenReturn(null);
            when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(existingTrajectory));
            when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TrajectoryEntity result = thermalEconomicService.processThermalEconomicParameterFile(
                    temp, "2023-2024", co2Entities, enerContentEntities, TrajectoryType.THERMAL_ECONOMIC_PARAMETER);

            assertNotNull(result);
            assertEquals(1, result.getVersion());
        } finally {
            Files.deleteIfExists(temp);
        }
    }


    @Test
    void buildThermalEconomicCo2ParameterValuesList_shouldReturnPopulatedListFromGeneratedExcel() throws Exception {
        Path temp = generateExcelFileWithCo2Rows(List.of(new String[]{"Gas", "FR", "2023", "100.5", "kg", "comment"},
                new String[]{"Oil", "FR", "2023", "200.0", "kg", "comment2"}));

        try {
            List<ThermalEconomicCo2Entity> result = thermalEconomicService.buildThermalEconomicCo2ParameterValuesList(temp, "2022-2023", 1);
            assertFalse(result.isEmpty());
            assertEquals(2, result.size());
            assertEquals("Gas", result.getFirst().getFuel());
            assertEquals("FR", result.getFirst().getCountry());
            assertEquals(2023, result.getFirst().getYear());
            assertEquals(new java.math.BigDecimal("100.5"), result.getFirst().getCo2EmissionFuel());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void buildThermalEconomicCo2ParameterValuesList_shouldSkipRowsNotMatchingHorizonFromGeneratedExcel() throws Exception {
        Path temp = generateExcelFileWithCo2Rows(java.util.List.of(
                new String[]{"Gas", "FR", "2024", "100.5", "kg", "comment"},
                new String[]{"Oil", "FR", "2025", "200.0", "kg", "comment2"}
        ));
        try {
            List<ThermalEconomicCo2Entity> result = thermalEconomicService.buildThermalEconomicCo2ParameterValuesList(temp, "2022-2023", 1);
            assertTrue(result.isEmpty());
        } finally {
            Files.deleteIfExists(temp);
        }
    }


    @Test
    void buildThermalEconomicCo2ParameterValuesList_shouldReturnEmptyListWhenSheetMissingInGeneratedExcel() throws Exception {
        Path temp = generateExcelFileWithDifferentSheet();
        try {
            List<ThermalEconomicCo2Entity> result = thermalEconomicService.buildThermalEconomicCo2ParameterValuesList(temp, "2023-2024", 1);
            assertTrue(result.isEmpty());
        } finally {
            Files.deleteIfExists(temp);
        }
    }


    @Test
    void buildThermalEconomicEnerContentParameterValuesList_shouldReturnPopulatedListFromGeneratedExcel() throws Exception {
        Path temp = generateExcelFileWithEnerRows(List.of(
                new String[]{"500", "MJ", "comment1"},
                new String[]{"1000", "MJ", "comment2"}
        ));

        try {
            List<ThermalEconomicEnerContentEntity> result = thermalEconomicService.buildThermalEconomicEnerContentParameterValuesList(temp, "2023-2024", 1);
            assertFalse(result.isEmpty());
            assertEquals(2, result.size());
            assertEquals(new BigDecimal("500"), result.get(0).getValue());
            assertEquals("MJ", result.get(0).getUnit());
            assertEquals("comment1", result.get(0).getComment());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void buildThermalEconomicEnerContentParameterValuesList_shouldReturnEmptyListWhenSheetMissingInGeneratedExcel() throws Exception {
        Path temp = generateExcelFileWithDifferentSheet();
        try {
            List<ThermalEconomicEnerContentEntity> result = thermalEconomicService.buildThermalEconomicEnerContentParameterValuesList(temp, "2023-2024", 1);
            assertTrue(result.isEmpty());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void buildThermalEconomicEnerContentParameterValuesList_shouldSkipRowsWithInvalidValues() throws Exception {
        Path temp = generateExcelFileWithEnerRows(List.of(
                new String[]{"invalid", "MJ", "comment1"},
                new String[]{"1000", "MJ", "comment2"}
        ));

        try {
            List<ThermalEconomicEnerContentEntity> result = thermalEconomicService.buildThermalEconomicEnerContentParameterValuesList(temp, "2023-2024", 1);
            assertEquals(1, result.size());
            assertEquals(new BigDecimal("1000"), result.get(0).getValue());
            assertEquals("MJ", result.get(0).getUnit());
            assertEquals("comment2", result.get(0).getComment());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path generateExcelFileWithEnerRows(List<String[]> rows) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("ener_content");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("value");
        header.createCell(1).setCellValue("unit");
        header.createCell(2).setCellValue("comment");
        int r = 1;
        for (String[] rowData : rows) {
            Row row = sheet.createRow(r++);
            for (int c = 0; c < rowData.length; c++) {
                String v = rowData[c];
                if (v == null) continue;
                try {
                    double dv = Double.parseDouble(v);
                    row.createCell(c).setCellValue(dv);
                } catch (NumberFormatException ex) {
                    row.createCell(c).setCellValue(v);
                }
            }
        }
        Path temp = Files.createTempFile("te_ener_", ".xlsx");
        try (java.io.OutputStream os = Files.newOutputStream(temp)) {
            wb.write(os);
        } finally {
            wb.close();
        }
        return temp;
    }

    private Path generateExcelFileWithCo2Rows(List<String[]> rows) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("CO2_emissions");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("fuel");
        header.createCell(1).setCellValue("country");
        header.createCell(2).setCellValue("year");
        header.createCell(3).setCellValue("co2");
        header.createCell(4).setCellValue("unit");
        header.createCell(5).setCellValue("comment");
        int r = 1;
        for (String[] rowData : rows) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
            for (int c = 0; c < rowData.length; c++) {
                String v = rowData[c];
                if (v == null) continue;
                try {
                    double dv = Double.parseDouble(v);
                    row.createCell(c).setCellValue(dv);
                } catch (NumberFormatException ex) {
                    row.createCell(c).setCellValue(v);
                }
            }
        }
        Path temp = Files.createTempFile("te_co2_", ".xlsx");
        try (java.io.OutputStream os = Files.newOutputStream(temp)) {
            wb.write(os);
        } finally {
            wb.close();
        }
        return temp;
    }

    private Path generateExcelFileWithDifferentSheet() throws Exception {
        org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        wb.createSheet("some_other_sheet");
        Path temp = Files.createTempFile("te_other_", ".xlsx");
        try (java.io.OutputStream os = Files.newOutputStream(temp)) {
            wb.write(os);
        } finally {
            wb.close();
        }
        return temp;
    }

}
