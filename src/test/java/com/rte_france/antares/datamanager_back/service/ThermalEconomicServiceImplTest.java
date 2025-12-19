package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicCo2Entity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicEnerContentEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalControlService;
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
import java.util.Collections;
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

    @Mock
    private ThermalControlService thermalControlService;


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
        Sheet sheet = generateExcelFileWithCo2Rows(List.of(new String[]{"Gas", "FR", "2023", "100.5", "kg", "comment"},
                new String[]{"Oil", "FR", "2023", "200.0", "kg", "comment2"}));


            List<ThermalEconomicCo2Entity> result = thermalEconomicService.buildThermalEconomicCo2ParameterValuesList("temp", "2022-2023", 1, sheet);
            assertFalse(result.isEmpty());
            assertEquals(2, result.size());
            assertEquals("Gas", result.getFirst().getFuel());
            assertEquals("FR", result.getFirst().getCountry());
            assertEquals(2023, result.getFirst().getYear());
            assertEquals(new java.math.BigDecimal("100.5"), result.getFirst().getCo2EmissionFuel());

    }

    @Test
    void buildThermalEconomicCo2ParameterValuesList_shouldThrowExceptionHorizonDoesNotExist() throws Exception {
        Sheet sheet = generateExcelFileWithCo2Rows(List.of(new String[]{"Gas", "FR", "2023", "100.5", "kg", "comment"},
                new String[]{"Oil", "FR", "2023", "200.0", "kg", "comment2"}));


        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> thermalEconomicService.buildThermalEconomicCo2ParameterValuesList("temp", "2023-2024", 1, sheet)
        );
        assertTrue(ex.getMessage().contains("Horizon does not exist in THERMAL Economic trajectory {0} in CO2_emissions tab"));


    }

    @Test
    void buildThermalEconomicCo2ParameterValuesList_shouldThrowBusinessExceptionWhenCo2IsNotValid() throws Exception {
        Sheet sheet = generateExcelFileWithCo2Rows(List.of(new String[]{"Gas", "FR", "2024", "100w.5", "kg", "comment"}, new String[]{"Oil", "FR", "2024", "200.0", "kg", "comment2"}));
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> thermalEconomicService.buildThermalEconomicCo2ParameterValuesList("temp", "2023-2024", 1, sheet)
            );
            assertTrue(ex.getMessage().contains("The value of CO2_EmissionFuel of horizon {0} in THERMAL Economic trajectory {1} in CO2_emissions  tab must be numeric"));

    }

    @Test
    void buildThermalEconomicCo2ParameterValuesList_shouldThrowBusinessExceptionWhenHorizonDoesNotExist() throws Exception {
        Sheet sheet = generateExcelFileWithCo2Rows(List.of());

            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> thermalEconomicService.buildThermalEconomicCo2ParameterValuesList("temp", "2023-2024", 1, sheet)
            );
            assertTrue(ex.getMessage().contains("No data in THERMAL Economic trajectory {0} in CO2_emissions tab"));


    }

    @Test
    void buildThermalEconomicEnerContentParameterValuesList_shouldReturnPopulatedListFromGeneratedExcel() throws Exception {
        Sheet sheet = generateExcelFileWithEnerRows(List.of(
                new String[]{"500", "MJ", "comment1"},
                new String[]{"1000", "MJ", "comment2"}
        ));


            List<ThermalEconomicEnerContentEntity> result = thermalEconomicService.buildThermalEconomicEnerContentParameterValuesList("temp", "2023-2024", 1,sheet);
            assertFalse(result.isEmpty());
            assertEquals(2, result.size());
            assertEquals(new BigDecimal("500"), result.get(0).getValue());
            assertEquals("MJ", result.get(0).getUnit());
            assertEquals("comment1", result.get(0).getComment());

    }

    @Test
    void buildThermalEconomicEnerContentParameterValuesList_shouldThrowExceptionWithOnlyHeader() throws Exception {
        Sheet sheet = generateExcelFileWithEnerRows(Collections.emptyList());

            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> thermalEconomicService.buildThermalEconomicEnerContentParameterValuesList("temp", "2023-2024", 1, sheet)
            );
            assertTrue(ex.getMessage().contains("No data in THERMAL Economic trajectory {0} in ener_content tab"));

    }
    @Test
    void buildThermalEconomicEnerContentParameterValuesList_shouldThrowBusinessExceptionWhenValueIsNotNumeric() throws Exception {
        Sheet sheet = generateExcelFileWithEnerRows(List.of(new String[]{"", "MJ", "comment1"}, new String[]{"1000", "MJ", "comment2"}));
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> thermalEconomicService.buildThermalEconomicEnerContentParameterValuesList("temp", "2023-2024", 1, sheet)

            );
            assertTrue(ex.getMessage().contains("The value of value of horizon {0} in THERMAL Economic trajectory {1} in ener_content  tab must be numeric"));

    }

    private Sheet generateExcelFileWithEnerRows(List<String[]> rows) throws Exception {
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
        return sheet;
    }

    private Sheet generateExcelFileWithCo2Rows(List<String[]> rows) throws Exception {
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
        return sheet;
    }

}
