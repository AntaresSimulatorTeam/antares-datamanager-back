package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostsRateEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
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

public class ThermalEconomicCostAndRateServiceImplTest {
    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private ThermalCostTypeRepository thermalCostTypeRepository;

    @InjectMocks
    private ThermalEconomicCostAndRateServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testBuildThermalEconomicRateValueList_shouldParseRateSheetCorrectly() throws Exception {

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

            Cell h0 = mock(Cell.class);
            Cell h1 = mock(Cell.class);
            Cell h2 = mock(Cell.class);
            //to mock 3 cells only
            when(header.getLastCellNum()).thenReturn((short) 3);
            when(header.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h0);
            when(header.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h1);
            when(header.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h2);
            when(h1.getCellType()).thenReturn(CellType.NUMERIC);
            when(h1.getNumericCellValue()).thenReturn(2021d);
            when(h2.getCellType()).thenReturn(CellType.NUMERIC);
            when(h2.getNumericCellValue()).thenReturn(2022d);

            Cell r0 = mock(Cell.class);
            Cell r1 = mock(Cell.class);
            Cell r2 = mock(Cell.class);
            when(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r0);
            when(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r1);
            when(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r2);
            when(r0.getCellType()).thenReturn(CellType.STRING);
            when(r0.getStringCellValue()).thenReturn("euro/dollar");
            when(r1.getCellType()).thenReturn(CellType.NUMERIC);
            when(r1.getNumericCellValue()).thenReturn(0.85);
            when(r2.getCellType()).thenReturn(CellType.NUMERIC);
            when(r2.getNumericCellValue()).thenReturn(0.95);


            List<ThermalCostsRateEntity> rates =
                    service.buildThermalEconomicRateValueList("costs_trajectoryTest", fakePath, 1);

            assertEquals(2, rates.size());
            assertEquals("euro/dollar", rates.get(0).getRateType());
            assertEquals(2021, rates.get(0).getYear());
            assertEquals(BigDecimal.valueOf(0.85), rates.get(0).getValue());
            assertEquals(2022, rates.get(1).getYear());
            assertEquals(BigDecimal.valueOf(0.95), rates.get(1).getValue());
        }
    }

    @Test
    void testBuildThermalEconomicCostsValueList_shouldParseCostsSheetCorrectly() throws Exception {

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
            Cell h5 = mock(Cell.class);
            when(header.getLastCellNum()).thenReturn((short) 6);
            when(header.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h0);
            when(header.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h1);
            when(header.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h2);
            when(header.getCell(5, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(h5);

            when(header.getCell(0)).thenReturn(h0);
            when(header.getCell(1)).thenReturn(h1);
            when(header.getCell(2)).thenReturn(h2);
            when(header.getCell(5)).thenReturn(h5);
            when(h0.getCellType()).thenReturn(CellType.STRING);
            when(h0.getStringCellValue()).thenReturn("");
            when(h1.getCellType()).thenReturn(CellType.NUMERIC);
            when(h1.getNumericCellValue()).thenReturn(2021d);
            when(h2.getCellType()).thenReturn(CellType.NUMERIC);
            when(h2.getNumericCellValue()).thenReturn(2022d);
            when(h5.getCellType()).thenReturn(CellType.STRING);
            when(h5.getStringCellValue()).thenReturn("NCV/HCV");

            Cell r0 = mock(Cell.class);
            Cell r1 = mock(Cell.class);
            Cell r2 = mock(Cell.class);
            Cell r3 = mock(Cell.class);
            Cell r4 = mock(Cell.class);
            Cell r5 = mock(Cell.class);
            when(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r0);
            when(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r1);
            when(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r2);
            when(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r3);
            when(row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r4);
            when(row.getCell(5, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(r5);

            when(r0.getCellType()).thenReturn(CellType.STRING);
            when(r0.getStringCellValue()).thenReturn("France"); // country
            when(r1.getCellType()).thenReturn(CellType.NUMERIC);
            when(r1.getNumericCellValue()).thenReturn(100.5); // cost for 2021
            when(r2.getCellType()).thenReturn(CellType.NUMERIC);
            when(r2.getNumericCellValue()).thenReturn(110.0); // cost for 2022
            when(r3.getCellType()).thenReturn(CellType.STRING);
            when(r3.getStringCellValue()).thenReturn("€/MWh"); // unit
            when(r4.getCellType()).thenReturn(CellType.STRING);
            when(r4.getStringCellValue()).thenReturn("Base modulation"); // modulation
            when(r5.getCellType()).thenReturn(CellType.NUMERIC);
            when(r5.getNumericCellValue()).thenReturn(0.9); // ratioNcvHcv

            when(r1.getCellType()).thenReturn(CellType.STRING);
            when(r1.getStringCellValue()).thenReturn("Gas");
            when(r2.getCellType()).thenReturn(CellType.NUMERIC);
            when(r2.getNumericCellValue()).thenReturn(110.0);

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
            assertEquals(110.0, cost.getCost());
            assertEquals(2022, cost.getYear());
        }
    }


    @Test
    void testBuildThermalEconomicCostValueList_shouldParseFullCostRowCorrectly() throws Exception {

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


            Cell[] headerCells = new Cell[6];
            for (int i = 0; i <= 5; i++) {
                headerCells[i] = mock(Cell.class);
                when(header.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                        .thenReturn(headerCells[i]);
                when(header.getCell(i)).thenReturn(headerCells[i]);
            }
            when(headerCells[5].getStringCellValue()).thenReturn("NCV/HCV"); // ratioNcvHcv header

            when(header.getLastCellNum()).thenReturn((short) 6);

            when(headerCells[0].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[0].getStringCellValue()).thenReturn("");
            when(headerCells[1].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[1].getStringCellValue()).thenReturn("");
            when(headerCells[3].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[3].getStringCellValue()).thenReturn("");
            when(headerCells[4].getCellType()).thenReturn(CellType.STRING);
            when(headerCells[4].getStringCellValue()).thenReturn("");

            when(headerCells[2].getCellType()).thenReturn(CellType.NUMERIC);
            when(headerCells[2].getNumericCellValue()).thenReturn(2025d);


            Cell[] rowCells = new Cell[6];
            for (int i = 0; i <= 5; i++) {
                rowCells[i] = mock(Cell.class);
                when(row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                        .thenReturn(rowCells[i]);
            }

            // Fill row values
            when(rowCells[0].getCellType()).thenReturn(CellType.STRING);
            when(rowCells[0].getStringCellValue()).thenReturn("Germany"); // country

            when(rowCells[1].getCellType()).thenReturn(CellType.STRING);
            when(rowCells[1].getStringCellValue()).thenReturn("Natural Gas"); // fuel

            when(rowCells[2].getCellType()).thenReturn(CellType.NUMERIC);
            when(rowCells[2].getNumericCellValue()).thenReturn(42.5); // cost value

            when(rowCells[3].getCellType()).thenReturn(CellType.STRING);
            when(rowCells[3].getStringCellValue()).thenReturn("€/MWh"); // unit

            when(rowCells[4].getCellType()).thenReturn(CellType.STRING);
            when(rowCells[4].getStringCellValue()).thenReturn("Base modulation"); // modulation

            when(rowCells[5].getCellType()).thenReturn(CellType.NUMERIC);
            when(rowCells[5].getNumericCellValue()).thenReturn(0.95); // ratioNcvHcv

            List<ThermalCostTypeEntity> costs = service.buildThermalEconomicCostValueList(
                    "costs_trajectoryTest", fakePath, "2025", 1);


            assertEquals(1, costs.size());

            ThermalCostTypeEntity type = costs.get(0);
            assertEquals("Germany", type.getCountry());
            assertEquals("Natural Gas", type.getFuel());
            assertEquals("€/MWh", type.getUnit());
            assertEquals("Base modulation", type.getModulation());
            assertEquals(0.95, type.getRatioNcvHcv());

            assertNotNull(type.getThermalCostEntities());
            assertEquals(1, type.getThermalCostEntities().size());

            ThermalCostEntity cost = type.getThermalCostEntities().get(0);
            assertEquals(42.5, cost.getCost());
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
                        ThermalCostEntity.builder().cost(10.0).year(2021).build()
                ))
                .build();

        ThermalCostsRateEntity rateEntity = ThermalCostsRateEntity.builder()
                .rateType("euro/dollar")
                .year(2021)
                .value(BigDecimal.valueOf(0.85))
                .build();

        when(thermalCostTypeRepository
                .findThermalCostTypeEntityByFuelAndCountry("Gas", "FR"))
                .thenReturn(Optional.empty());
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
    void testBuildThermalEconomicRateValueList_shouldThrowBusinessExceptionWhenFileMissing() throws Exception {
        Path fakePath = mock(Path.class);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.newInputStream(fakePath))
                    .thenThrow(new IOException("File not found"));

            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> service.buildThermalEconomicRateValueList("costs_trajectoryTest", fakePath, 1)
            );

            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
            assertTrue(ex.getMessage().contains("Could not read thermal economic rate file"));
        }
    }


    @Test
    void testBuildThermalEconomicCostValueList_shouldThrowBusinessExceptionWhenFileMissing() throws Exception {
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
}
