package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.repository.model.StConstraintsHoursEntity;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;
import com.rte_france.antares.datamanager_back.service.sts.StStorageConstraintsFileProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.util.Utils.getCellValue;

@Slf4j
@Service
@RequiredArgsConstructor
public class StStorageConstraintsFileProcessorServiceImpl implements StStorageConstraintsFileProcessorService {
    @Override
    public List<StConstraintsParameterEntity> processConstraintsParametersAnHoursFile(Path additionalConstraintsPath) throws IOException {
        List<StConstraintsParameterEntity> parameters = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(additionalConstraintsPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            {

                //"parameters"
                Sheet parametersSheet = workbook.getSheet("parameters");
                processParameters(parametersSheet, parameters);

                //"hours"
                Sheet hoursSheet = workbook.getSheet("hours");
                processHours(hoursSheet, parameters);
            }


            return parameters;
        }


    }

    private void processHours(Sheet hoursSheet, List<StConstraintsParameterEntity> parameters) {
        if (hoursSheet != null) {
            for (Row row : hoursSheet) {
                if (row.getRowNum() == 0) continue; // skip header
                String paramName = Optional.ofNullable(getCellValue(row, 0))
                        .map(Object::toString)
                        .map(String::trim)
                        .orElseThrow(() -> new IllegalStateException("Parameter name missing in hours sheet"));

                String paramZone = Optional.ofNullable(getCellValue(row, 1))
                        .map(Object::toString)
                        .map(String::trim)
                        .orElseThrow(() -> new IllegalStateException("Parameter zone missing in hours sheet"));

                String paramCluster = Optional.ofNullable(getCellValue(row, 2))
                        .map(Object::toString)
                        .map(String::trim)
                        .orElseThrow(() -> new IllegalStateException("Parameter cluster missing in hours sheet"));

                // Filter name + zone + cluster
                StConstraintsParameterEntity param = parameters.stream()
                        .filter(p -> paramName.equals(p.getName())
                                && paramZone.equals(p.getZone())
                                && paramCluster.equals(p.getCluster()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Parameter not found: name=" + paramName + ", zone=" + paramZone + ", cluster=" + paramCluster));

                StConstraintsHoursEntity hour = getStConstraintsHoursEntity(row, param);
                param.getHours().add(hour);
            }
        }
    }

    private StConstraintsHoursEntity getStConstraintsHoursEntity(Row row, StConstraintsParameterEntity param) {
        StConstraintsHoursEntity hour = new StConstraintsHoursEntity();
        hour.setOccurrence(getIntegerCellValue(row, 3, 0));

        hour.setStartHour(getIntegerCellValue(row, 4, 0));

        hour.setEndHour(getIntegerCellValue(row, 5, 0));

        hour.setParameter(param);
        return hour;
    }

    private void processParameters(Sheet parametersSheet, List<StConstraintsParameterEntity> parameters) {
        if (parametersSheet != null) {
            for (Row row : parametersSheet) {
                if (row.getRowNum() == 0) continue; // skip header

                StConstraintsParameterEntity param = new StConstraintsParameterEntity();
                param.setHours(new ArrayList<>());

                param.setName(getStringCellValue(row, 0));
                param.setZone(getStringCellValue(row, 1));
                param.setCluster(getStringCellValue(row, 2));
                param.setVariable(getStringCellValue(row, 3));
                param.setOperator(getStringCellValue(row, 4));
                param.setEnabled(getBooleanCellValue(row));

                parameters.add(param);
            }
        }
    }

    private String getStringCellValue(Row row, int index) {
        return Optional.ofNullable(getCellValue(row, index))
                .map(Object::toString)
                .map(String::trim)
                .orElse(null);
    }

    private boolean getBooleanCellValue(Row row) {
        Cell cell = row.getCell(5);
        if (cell == null) return false;
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case STRING -> Boolean.parseBoolean(cell.getStringCellValue().trim());
            case NUMERIC -> cell.getNumericCellValue() != 0;
            default -> false;
        };
    }

    private int getIntegerCellValue(Row row, int index, int defaultValue) {
        return Optional.ofNullable(getCellValue(row, index))
                .map(obj -> {
                    if (obj instanceof Double d) return d.intValue();
                    try {
                        return Integer.parseInt(obj.toString().trim());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}

