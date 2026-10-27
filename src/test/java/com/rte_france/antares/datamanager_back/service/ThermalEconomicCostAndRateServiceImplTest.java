package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostsRateEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.TrajectoryService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalControlService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalEconomicCostAndRateServiceImpl;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ThermalEconomicCostAndRateServiceImplTest {
    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private TrajectoryService trajectoryService;

    @Mock
    private ThermalCostTypeRepository thermalCostTypeRepository;

    @Mock
    private ThermalControlService thermalControlService;

    @InjectMocks
    private ThermalEconomicCostAndRateServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void testBuildThermalEconomicCostsValueList_shouldParseCostsSheetCorrectly() {

        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        Row header = mock(Row.class);
        Row row = mock(Row.class);

        Path fakePath = mock(Path.class);
        InputStream fakeInput = mock(InputStream.class);


        try (MockedStatic<WorkbookFactory> workbookFactoryMock = mockStatic(WorkbookFactory.class);
             MockedStatic<Files> filesMock = mockStatic(Files.class)) {

            filesMock.when(() -> Files.newInputStream(fakePath)).thenReturn(fakeInput);
            workbookFactoryMock.when(() -> WorkbookFactory.create(fakeInput)).thenReturn(workbook);

            when(workbook.getSheet("costs")).thenReturn(sheet);
            when(sheet.getRow(0)).thenReturn(header);
            when(sheet.getRow(1)).thenReturn(row);
            when(sheet.getLastRowNum()).thenReturn(1);

            Cell h0 = mock(Cell.class);
            Cell h1 = mock(Cell.class);
            Cell h2 = mock(Cell.class);
            Cell h3 = mock(Cell.class);
            Cell h4 = mock(Cell.class);
            Cell h5 = mock(Cell.class);
            Cell h6 = mock(Cell.class);
            when(header.getLastCellNum()).thenReturn((short) 7);
            when(header.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h0);
            when(header.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h1);
            when(header.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h2);
            when(header.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h3);
            when(header.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h4);
            when(header.getCell(5, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h5);
            when(header.getCell(6, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h6);

            when(header.getCell(0)).thenReturn(h0);
            when(header.getCell(1)).thenReturn(h1);
            when(header.getCell(2)).thenReturn(h2);
            when(header.getCell(3)).thenReturn(h3);
            when(header.getCell(4)).thenReturn(h4);
            when(header.getCell(5)).thenReturn(h5);
            when(header.getCell(6)).thenReturn(h6);

            when(h0.getCellType()).thenReturn(CellType.STRING);
            when(h0.getStringCellValue()).thenReturn("country");
            when(h1.getCellType()).thenReturn(CellType.STRING);
            when(h1.getStringCellValue()).thenReturn("fuel");
            when(h2.getCellType()).thenReturn(CellType.STRING);
            when(h2.getStringCellValue()).thenReturn("comment");
            when(h3.getCellType()).thenReturn(CellType.STRING);
            when(h3.getStringCellValue()).thenReturn("unit");
            when(h4.getCellType()).thenReturn(CellType.STRING);
            when(h4.getStringCellValue()).thenReturn("modulation");
            when(h5.getCellType()).thenReturn(CellType.STRING);
            when(h5.getStringCellValue()).thenReturn("ratio_ncv_hcv");
            when(h6.getCellType()).thenReturn(CellType.STRING);
            when(h6.getStringCellValue()).thenReturn("2022");

            Cell r0 = mock(Cell.class);
            Cell r1 = mock(Cell.class);
            Cell r2 = mock(Cell.class);
            Cell r3 = mock(Cell.class);
            Cell r4 = mock(Cell.class);
            Cell r5 = mock(Cell.class);
            Cell r6 = mock(Cell.class);
            when(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r0);
            when(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r1);
            when(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r2);
            when(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r3);
            when(row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r4);
            when(row.getCell(5, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r5);
            when(row.getCell(6, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r6);

            when(r0.getCellType()).thenReturn(CellType.STRING);
            when(r0.getStringCellValue()).thenReturn("France"); // country
            when(r1.getCellType()).thenReturn(CellType.STRING);
            when(r1.getStringCellValue()).thenReturn("Gas"); // fuel
            when(r2.getCellType()).thenReturn(CellType.STRING);
            when(r2.getStringCellValue()).thenReturn("comment"); // comment
            when(r3.getCellType()).thenReturn(CellType.STRING);
            when(r3.getStringCellValue()).thenReturn("€/MWh"); // unit
            when(r4.getCellType()).thenReturn(CellType.STRING);
            when(r4.getStringCellValue()).thenReturn("Base modulation"); // modulation
            when(r5.getCellType()).thenReturn(CellType.NUMERIC);
            when(r5.getNumericCellValue()).thenReturn(0.9); // ratioNcvHcv
            when(r6.getCellType()).thenReturn(CellType.NUMERIC);
            when(r6.getNumericCellValue()).thenReturn(110.0); // cost for 2022

            List<ThermalCostTypeEntity> costs =
                    service.buildThermalEconomicCostValueList("trajectoryTest", fakePath, "2022", 1);

            assertEquals(1, costs.size());
            ThermalCostTypeEntity type = costs.get(0);
            assertEquals("France", type.getCountry());
            assertEquals("Gas", type.getFuel());
            assertEquals("€/MWh", type.getUnit());
            assertEquals("Base modulation", type.getModulation());
            assertEquals(0.9, type.getRatioNcvHcv());

            assertNotNull(type.getThermalCostEntities());
            assertEquals(1, type.getThermalCostEntities().size());
            ThermalCostEntity cost = type.getThermalCostEntities().get(0);
            assertEquals(BigDecimal.valueOf(110.0), cost.getCost());
            assertEquals(2022, cost.getYear());
        }
    }


    @Test
    void testBuildThermalEconomicCostValueList_shouldParseFullCostRowCorrectly() {

        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        Row header = mock(Row.class);
        Row row = mock(Row.class);

        Path fakePath = mock(Path.class);
        InputStream fakeInput = mock(InputStream.class);

        try (MockedStatic<WorkbookFactory> workbookFactoryMock = mockStatic(WorkbookFactory.class);
             MockedStatic<Files> filesMock = mockStatic(Files.class)) {

            filesMock.when(() -> Files.newInputStream(fakePath)).thenReturn(fakeInput);
            workbookFactoryMock.when(() -> WorkbookFactory.create(fakeInput)).thenReturn(workbook);

            when(workbook.getSheet("costs")).thenReturn(sheet);
            when(sheet.getRow(0)).thenReturn(header);
            when(sheet.getRow(1)).thenReturn(row);
            when(sheet.getLastRowNum()).thenReturn(1);


            Cell[] headerCells = new Cell[7];
            for (int i = 0; i <= 6; i++) {
                headerCells[i] = mock(Cell.class);
                when(header.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                        .thenReturn(headerCells[i]);
                when(header.getCell(i)).thenReturn(headerCells[i]);
            }
            when(header.getLastCellNum()).thenReturn((short) 7);

            when(headerCells[0].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[0].getStringCellValue()).thenReturn("country");
            when(headerCells[1].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[1].getStringCellValue()).thenReturn("fuel");
            when(headerCells[2].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[2].getStringCellValue()).thenReturn("comment");
            when(headerCells[3].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[3].getStringCellValue()).thenReturn("unit");
            when(headerCells[4].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[4].getStringCellValue()).thenReturn("modulation");
            when(headerCells[5].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[5].getStringCellValue()).thenReturn("ratio_ncv_hcv");
            when(headerCells[6].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[6].getStringCellValue()).thenReturn("2025");



            Cell[] rowCells = new Cell[7];
            for (int i = 0; i <= 6; i++) {
                rowCells[i] = mock(Cell.class);
                when(row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                        .thenReturn(rowCells[i]);
                when(row.getCell(i)).thenReturn(rowCells[i]);
            }

            // Fill row values
            when(rowCells[0].getCellType()).thenReturn(CellType.STRING);
            when(rowCells[0].getStringCellValue()).thenReturn("Germany"); // country

            when(rowCells[1].getCellType()).thenReturn(CellType.STRING);
            when(rowCells[1].getStringCellValue()).thenReturn("Natural Gas"); // fuel

            when(rowCells[2].getCellType()).thenReturn(CellType.STRING);
            when(rowCells[2].getStringCellValue()).thenReturn("Sample Comment"); // cost value

            when(rowCells[3].getCellType()).thenReturn(CellType.STRING);
            when(rowCells[3].getStringCellValue()).thenReturn("€/MWh"); // unit

            when(rowCells[4].getCellType()).thenReturn(CellType.STRING);
            when(rowCells[4].getStringCellValue()).thenReturn("Base modulation"); // modulation

            when(rowCells[5].getCellType()).thenReturn(CellType.NUMERIC);
            when(rowCells[5].getNumericCellValue()).thenReturn(0.95); // ratioNcvHcv

            when(rowCells[6].getCellType()).thenReturn(CellType.NUMERIC);
            when(rowCells[6].getNumericCellValue()).thenReturn(42.5);

            List<ThermalCostTypeEntity> costs = service.buildThermalEconomicCostValueList(
                    "costs_trajectoryTest", fakePath, "2025", 1);


            assertEquals(1, costs.size());

            ThermalCostTypeEntity type = costs.get(0);
            assertEquals("Germany", type.getCountry());
            assertEquals("Natural Gas", type.getFuel());
            assertEquals("Sample Comment", type.getComment());
            assertEquals("€/MWh", type.getUnit());
            assertEquals("Base modulation", type.getModulation());
            assertEquals(0.95, type.getRatioNcvHcv());

            assertNotNull(type.getThermalCostEntities());
            assertEquals(1, type.getThermalCostEntities().size());

            ThermalCostEntity cost = type.getThermalCostEntities().get(0);
            assertEquals(BigDecimal.valueOf(42.5), cost.getCost());
            assertEquals(2025, cost.getYear());
        }
    }



    @Test
    void testSaveThermalEconomicCostAndRateTrajectory_shouldLinkAndSaveAll() {

        TrajectoryEntity trajectory = new TrajectoryEntity();

        ThermalCostTypeEntity costType = ThermalCostTypeEntity.builder()
                .fuel("Gas")
                .country("FR")
                .thermalCostEntities(List.of(
                        ThermalCostEntity.builder().cost(BigDecimal.valueOf(10.0)).year(2021).build()
                ))
                .build();

        ThermalCostsRateEntity rateEntity = ThermalCostsRateEntity.builder()
                .rateType("euro/dollar")
                .year(2021)
                .value(BigDecimal.valueOf(0.85))
                .build();

        when(thermalCostTypeRepository
                .findByFuelIgnoreCase("Gas"))
                .thenReturn(Optional.of(costType));
        when(thermalCostTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TrajectoryEntity result = service.saveThermalEconomicCostAndRateTrajectory(
                trajectory,
                List.of(costType),
                List.of(rateEntity),
                TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER
        );


        assertNotNull(result.getThermalCosts());
        assertEquals(1, result.getThermalCosts().size());
        assertEquals("Gas", result.getThermalCosts().get(0).getThermalType().getFuel());
        assertEquals(trajectory, result.getThermalCosts().get(0).getTrajectory());

        assertNotNull(result.getThermalCostsRates());
        assertEquals(1, result.getThermalCostsRates().size());
        assertEquals(trajectory, result.getThermalCostsRates().get(0).getTrajectory());

        verify(trajectoryRepository, times(1)).save(trajectory);
    }


    @Test
    void testBuildThermalEconomicRateValueList_shouldThrowBusinessExceptionWhenFileMissing() {
        Path fakePath = mock(Path.class);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.newInputStream(fakePath))
                    .thenThrow(new IOException("File not found"));

            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> service.buildThermalEconomicRateValueList("costs_trajectoryTest", fakePath, "2022",1)
            );

            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
            assertTrue(ex.getMessage().contains("Could not read thermal economic rate file"));
        }
    }


    @Test
    void testBuildThermalEconomicCostValueList_shouldThrowBusinessExceptionWhenFileMissing() {
        Path fakePath = mock(Path.class);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.newInputStream(fakePath))
                    .thenThrow(new IOException("File not found"));

            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> service.buildThermalEconomicCostValueList("costs_trajectoryTest", fakePath,"2022", 1)
            );

            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
            assertTrue(ex.getMessage().contains("Could not read thermal economic costs file"));
        }
    }

    @Test
    void buildThermalEconomicCostValueList_shouldThrowBusinessException_whenSheetIsNull() {
        Workbook workbook = mock(Workbook.class);
        Path fakePath = mock(Path.class);
        InputStream fakeInput = mock(InputStream.class);

        try (MockedStatic<WorkbookFactory> workbookFactoryMock = mockStatic(WorkbookFactory.class);
             MockedStatic<Files> filesMock = mockStatic(Files.class)) {

            filesMock.when(() -> Files.newInputStream(fakePath)).thenReturn(fakeInput);
            workbookFactoryMock.when(() -> WorkbookFactory.create(fakeInput)).thenReturn(workbook);

            when(workbook.getSheet("costs")).thenReturn(null);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.buildThermalEconomicCostValueList("costs_trajectoryTest", fakePath, "2025", 1)
            );

            assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
            assertEquals("Sheet 'costs' is missing in trajectory {0}", exception.getMessage());
            assertEquals(List.of("costs_trajectoryTest"), exception.getErrorMessageArguments());
        }
    }

    @Test
    void buildThermalEconomicCostValueList_shouldThrowBusinessException_whenHeaderIsNull() {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        Path fakePath = mock(Path.class);
        InputStream fakeInput = mock(InputStream.class);

        try (MockedStatic<WorkbookFactory> workbookFactoryMock = mockStatic(WorkbookFactory.class);
             MockedStatic<Files> filesMock = mockStatic(Files.class)) {

            filesMock.when(() -> Files.newInputStream(fakePath)).thenReturn(fakeInput);
            workbookFactoryMock.when(() -> WorkbookFactory.create(fakeInput)).thenReturn(workbook);

            when(workbook.getSheet("costs")).thenReturn(sheet);
            when(sheet.getRow(0)).thenReturn(null);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.buildThermalEconomicCostValueList("costs_trajectoryTest", fakePath, "2025", 1)
            );

            assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
            assertEquals("Header row is missing in 'costs' sheet for trajectory {0}", exception.getMessage());
            assertEquals(List.of("costs_trajectoryTest"), exception.getErrorMessageArguments());
        }
    }

    @Test
    void buildThermalEconomicRateValueList_shouldParseRateSheetWithValidData() {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        Row header = mock(Row.class);
        Row row = mock(Row.class);

        Path fakePath = mock(Path.class);
        InputStream fakeInput = mock(InputStream.class);

        try (MockedStatic<WorkbookFactory> workbookFactoryMock = mockStatic(WorkbookFactory.class);
             MockedStatic<Files> filesMock = mockStatic(Files.class)) {

            filesMock.when(() -> Files.newInputStream(fakePath)).thenReturn(fakeInput);
            workbookFactoryMock.when(() -> WorkbookFactory.create(fakeInput)).thenReturn(workbook);

            when(workbook.getSheet("rate")).thenReturn(sheet);
            when(sheet.getRow(0)).thenReturn(header);
            when(sheet.getRow(1)).thenReturn(row);
            when(sheet.getLastRowNum()).thenReturn(1);

            when(header.getLastCellNum()).thenReturn((short) 2);

            Cell h0 = mock(Cell.class);
            Cell h1 = mock(Cell.class);
            when(header.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h0);
            when(header.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h1);
            when(header.getCell(0)).thenReturn(h0);
            when(header.getCell(1)).thenReturn(h1);
            when(h0.getCellType()).thenReturn(CellType.STRING);
            when(h0.getStringCellValue()).thenReturn("Rate Type");
            when(h1.getCellType()).thenReturn(CellType.NUMERIC);
            when(h1.getNumericCellValue()).thenReturn(2022d);

            Cell r0 = mock(Cell.class);
            Cell r1 = mock(Cell.class);
            when(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r0);
            when(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r1);
            when(row.getCell(0)).thenReturn(r0);
            when(row.getCell(1)).thenReturn(r1);

            when(r0.getCellType()).thenReturn(CellType.STRING);
            when(r0.getStringCellValue()).thenReturn("Exchange Rate");
            when(r1.getCellType()).thenReturn(CellType.NUMERIC);
            when(r1.getNumericCellValue()).thenReturn(1.15);

            List<ThermalCostsRateEntity> rates = service.buildThermalEconomicRateValueList("testTrajectory", fakePath, "2022", 1);

            assertEquals(1, rates.size());
            ThermalCostsRateEntity rate = rates.get(0);
            assertEquals("Exchange Rate", rate.getRateType());
            assertEquals(BigDecimal.valueOf(1.15), rate.getValue());
            assertEquals(2022, rate.getYear());
        }
    }


    @Test
    void buildThermalEconomicRateValueList_shouldHandleEmptySheetGracefully() {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        Row header = mock(Row.class);

        Path fakePath = mock(Path.class);
        InputStream fakeInput = mock(InputStream.class);

        try (MockedStatic<WorkbookFactory> workbookFactoryMock = mockStatic(WorkbookFactory.class);
             MockedStatic<Files> filesMock = mockStatic(Files.class)) {

            filesMock.when(() -> Files.newInputStream(fakePath)).thenReturn(fakeInput);
            workbookFactoryMock.when(() -> WorkbookFactory.create(fakeInput)).thenReturn(workbook);

            when(workbook.getSheet("rate")).thenReturn(sheet);
            // return an empty header row (no cells) instead of null to avoid NPE in Utils
            when(sheet.getRow(0)).thenReturn(header);
            when(header.getLastCellNum()).thenReturn((short) 0);

            List<ThermalCostsRateEntity> rates = service.buildThermalEconomicRateValueList("testTrajectory", fakePath, "2022", 1);

            assertTrue(rates.isEmpty());
        }
    }

    @Test
    void buildThermalEconomicRateValueList_shouldSkipRowsWithBlankRateType() {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        Row header = mock(Row.class);
        Row row = mock(Row.class);

        Path fakePath = mock(Path.class);
        InputStream fakeInput = mock(InputStream.class);

        try (MockedStatic<WorkbookFactory> workbookFactoryMock = mockStatic(WorkbookFactory.class);
             MockedStatic<Files> filesMock = mockStatic(Files.class)) {

            filesMock.when(() -> Files.newInputStream(fakePath)).thenReturn(fakeInput);
            workbookFactoryMock.when(() -> WorkbookFactory.create(fakeInput)).thenReturn(workbook);

            when(workbook.getSheet("rate")).thenReturn(sheet);
            when(sheet.getRow(0)).thenReturn(header);
            when(sheet.getRow(1)).thenReturn(row);
            when(sheet.getLastRowNum()).thenReturn(1);

            when(header.getLastCellNum()).thenReturn((short) 1);

            Cell h0 = mock(Cell.class);
            when(header.getCell(0)).thenReturn(h0);
            when(h0.getCellType()).thenReturn(CellType.STRING);
            when(h0.getStringCellValue()).thenReturn("Rate Type");

            Cell r0 = mock(Cell.class);
            when(row.getCell(0)).thenReturn(r0);
            when(r0.getCellType()).thenReturn(CellType.STRING);
            when(r0.getStringCellValue()).thenReturn("");

            List<ThermalCostsRateEntity> rates = service.buildThermalEconomicRateValueList("testTrajectory", fakePath, "2022", 1);

            assertTrue(rates.isEmpty());
        }
    }


}
