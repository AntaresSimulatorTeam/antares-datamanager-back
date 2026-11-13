package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.AntaresExceptionHandler;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalControlService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThermalCostSheetValidatorTest {
    private static final String TRAJECTORY = "costs_test";
    private static final String[] HEADERS = {"fuel", "comment", "unit", "modulation", "ratio_NCV_HCV"};

    @TempDir
    Path tempDir;

    private Sheet sheet(XSSFWorkbook workbook, boolean country, boolean numericYear) {
        Sheet sheet = workbook.createSheet("costs");
        Row header = sheet.createRow(0);
        int offset = country ? 1 : 0;
        if (country) {
            header.createCell(0).setCellValue("country");
        }
        for (int i = 0; i < HEADERS.length; i++) {
            header.createCell(i + offset).setCellValue(HEADERS[i]);
        }
        Cell year = header.createCell(HEADERS.length + offset);
        if (numericYear) {
            year.setCellValue(2025);
        } else {
            year.setCellValue("2025");
        }
        Row data = sheet.createRow(1);
        if (country) {
            data.createCell(0).setCellValue("Europe");
        }
        data.createCell(offset).setCellValue("Gas");
        data.createCell(offset + 1).setCellValue("comment");
        data.createCell(offset + 2).setCellValue("unit");
        data.createCell(offset + 3).setCellValue("base");
        data.createCell(offset + 4).setCellValue(1.11);
        data.createCell(offset + 5).setCellValue(42);
        return sheet;
    }

    private String message(BusinessException error) {
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        return MessageFormat.format(error.getMessage(), error.getErrorMessageArguments().toArray());
    }

    @Test
    void acceptsBothLayoutsAndYearTypes() throws Exception {
        for (boolean country : List.of(false, true)) {
            for (boolean numericYear : List.of(false, true)) {
                try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                    Sheet sheet = sheet(workbook, country, numericYear);
                    assertEquals(country ? 1 : 0, ThermalCostSheetValidator.validate(sheet, TRAJECTORY));
                    Sheet rate = workbook.createSheet("rate");
                    rate.createRow(0).createCell(1).setCellValue("2025");
                    rate.createRow(1).createCell(1).setCellValue(1.0);
                    Path path = tempDir.resolve("cost-" + country + "-" + numericYear + ".xlsx");
                    try (OutputStream out = Files.newOutputStream(path)) {
                        workbook.write(out);
                    }
                    var controls = new ThermalControlsServiceImpl(null, null);
                    assertDoesNotThrow(() -> controls.verifyCostsTrajectory("2025", path, TRAJECTORY, 1));
                    var service = new ThermalEconomicCostAndRateServiceImpl(mock(TrajectoryRepository.class),
                            mock(ThermalControlService.class), mock(ThermalCostTypeRepository.class));
                    var result = service.buildThermalEconomicCostValueList(TRAJECTORY, path, "2025", 1);
                    assertEquals(1, result.size());
                    assertEquals(country ? "Europe" : null, result.getFirst().getCountry());
                    assertEquals("Gas", result.getFirst().getFuel());
                    assertEquals(1.11, result.getFirst().getRatioNcvHcv());
                    assertEquals(2025, result.getFirst().getThermalCostEntities().getFirst().getYear());
                }
            }
        }
    }

    @Test
    void reportsMissingAndMisplacedColumnsAndMissingYear() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = sheet(workbook, false, false);
            Row header = sheet.getRow(0);
            header.getCell(0).setCellValue("unit");
            header.getCell(2).setCellValue("fuel");
            header.removeCell(header.getCell(1));
            header.removeCell(header.getCell(5));
            BusinessException error = assertThrows(BusinessException.class,
                    () -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY));
            assertEquals("Missing columns fuel, comment, unit, YYYY in Thermal Costs trajectory costs_test", message(error));
        }
    }

    @Test
    void reportsCountryWhenMisplaced() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = sheet(workbook, false, false);
            sheet.getRow(0).createCell(6).setCellValue("country");
            assertEquals("Missing columns country in Thermal Costs trajectory costs_test",
                    message(assertThrows(BusinessException.class, () -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY))));
        }
    }

    @Test
    void rejectsUnrecognizedAndDuplicateYearHeaders() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = sheet(workbook, true, true);
            sheet.getRow(0).createCell(7).setCellValue("scenario");
            sheet.getRow(0).createCell(8).setCellValue(2025);
            assertEquals("Unexpected columns scenario, 2025 in Thermal Costs trajectory costs_test",
                    message(assertThrows(BusinessException.class, () -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY))));
        }
    }

    @Test
    void acceptsNormalizedHeadersAndMultipleYearsButRejectsMisplacedKnownHeaders() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = sheet(workbook, false, false);
            sheet.getRow(0).getCell(0).setCellValue(" FUEL ");
            sheet.getRow(0).createCell(6).setCellValue(2030);
            assertDoesNotThrow(() -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY));
            sheet.getRow(0).createCell(7).setCellValue("fuel");
            assertEquals("Missing columns fuel in Thermal Costs trajectory costs_test",
                    message(assertThrows(BusinessException.class, () -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY))));
        }
    }

    @Test
    void lengthLimitsAreInclusiveAndAggregateAllColumns() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = sheet(workbook, false, true);
            Row row = sheet.getRow(1);
            row.getCell(0).setCellValue("f".repeat(20));
            row.getCell(1).setCellValue("c".repeat(255));
            row.getCell(2).setCellValue("u".repeat(20));
            row.getCell(3).setCellValue("m".repeat(20));
            assertDoesNotThrow(() -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY));
            row.getCell(0).setCellValue("f".repeat(21));
            Row other = sheet.createRow(2);
            other.createCell(2).setCellValue("u".repeat(21));
            other.createCell(3).setCellValue("m".repeat(21));
            assertEquals("Columns fuel, unit, modulation value cannot exceed 20 characters in Thermal Costs trajectory costs_test",
                    message(assertThrows(BusinessException.class, () -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY))));
            row.getCell(0).setCellValue("f");
            other.getCell(2).setCellValue("u");
            other.getCell(3).setCellValue("m");
            row.getCell(1).setCellValue("c".repeat(256));
            assertEquals("Column comment value cannot exceed 255 characters in Thermal Costs trajectory costs_test",
                    message(assertThrows(BusinessException.class, () -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY))));
        }
    }

    @Test
    void countryHasTenCharacterLimitWhenPresent() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = sheet(workbook, true, true);
            sheet.getRow(1).getCell(0).setCellValue("c".repeat(10));
            assertDoesNotThrow(() -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY));
            sheet.getRow(1).getCell(0).setCellValue("c".repeat(11));
            assertEquals("Column country value cannot exceed 10 characters in Thermal Costs trajectory costs_test",
                    message(assertThrows(BusinessException.class, () -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY))));
        }
    }

    @Test
    void acceptsBlankNumericStringAndNumericFormulaButRejectsOtherValues() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = sheet(workbook, false, false);
            Cell ratio = sheet.getRow(1).getCell(4);
            ratio.setCellValue("1.234");
            assertDoesNotThrow(() -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY));
            ratio.setCellFormula("1/2");
            assertDoesNotThrow(() -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY));
            ratio.setBlank();
            assertDoesNotThrow(() -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY));
            ratio.setCellValue(" ");
            assertDoesNotThrow(() -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY));
            ratio.setCellValue("not numeric");
            assertEquals("Column ratio_ncv_hcv must be numeric in Thermal Costs trajectory costs_test",
                    message(assertThrows(BusinessException.class, () -> ThermalCostSheetValidator.validate(sheet, TRAJECTORY))));
        }
    }

    @Test
    void rejectsInvalidInputBeforeOtherChecksOrPersistence() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet costs = sheet(workbook, false, true);
            costs.getRow(0).getCell(0).setCellValue("bad");
            workbook.createSheet("rate");
            Path path = tempDir.resolve("invalid.xlsx");
            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
            var controls = new ThermalControlsServiceImpl(null, null);
            BusinessException error = assertThrows(BusinessException.class,
                    () -> controls.verifyCostsTrajectory("2030", path, TRAJECTORY, 1));
            assertEquals("Missing columns fuel in Thermal Costs trajectory costs_test", message(error));
            var response = new AntaresExceptionHandler().businessExceptionHandler(error);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Missing columns fuel in Thermal Costs trajectory costs_test",
                    response.getBody().getAntaresErrorMessage());
            assertEquals("BUSINESS", response.getBody().getType());
            var repository = mock(ThermalCostTypeRepository.class);
            var service = new ThermalEconomicCostAndRateServiceImpl(mock(TrajectoryRepository.class),
                    mock(ThermalControlService.class), repository);
            assertThrows(BusinessException.class,
                    () -> service.buildThermalEconomicCostValueList(TRAJECTORY, path, "2030", 1));
            verifyNoInteractions(repository);
        }
    }

    @Test
    void emptyRatioIsImportedAsNull() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet costs = sheet(workbook, false, false);
            costs.getRow(1).getCell(4).setCellValue(" ");
            Path path = tempDir.resolve("empty-ratio.xlsx");
            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
            var service = new ThermalEconomicCostAndRateServiceImpl(mock(TrajectoryRepository.class),
                    mock(ThermalControlService.class), mock(ThermalCostTypeRepository.class));
            assertNull(service.buildThermalEconomicCostValueList(TRAJECTORY, path, "2025", 1)
                    .getFirst().getRatioNcvHcv());
        }
    }
}
