package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.repository.model.StConstraintsHoursEntity;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;
import com.rte_france.antares.datamanager_back.service.sts.StStorageConstraintsFileProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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

                StConstraintsHoursEntity hour = new StConstraintsHoursEntity();
                hour.setOccurrence(Optional.ofNullable(getCellValue(row, 3))
                        .map(obj -> obj instanceof Double d ? d.intValue() : Integer.parseInt(obj.toString().trim()))
                        .orElse(0));

                hour.setStartHour(Optional.ofNullable(getCellValue(row, 4))
                        .map(obj -> obj instanceof Double d ? d.intValue() : Integer.parseInt(obj.toString().trim()))
                        .orElse(0));

                hour.setEndHour(Optional.ofNullable(getCellValue(row, 5))
                        .map(obj -> obj instanceof Double d ? d.intValue() : Integer.parseInt(obj.toString().trim()))
                        .orElse(0));

                // Lien bidirectionnel
                hour.setParameter(param);
                param.getHours().add(hour);
            }
        }
    }

    private void processParameters(Sheet parametersSheet, List<StConstraintsParameterEntity> parameters) {
        if (parametersSheet != null) {
            for (Row row : parametersSheet) {
                if (row.getRowNum() == 0) continue; // skip header
                StConstraintsParameterEntity param = new StConstraintsParameterEntity();

                param.setHours(new ArrayList<>());

                param.setName(Optional.ofNullable(getCellValue(row, 0))
                        .map(Object::toString)
                        .map(String::trim)
                        .orElse(null));

                param.setZone(Optional.ofNullable(getCellValue(row, 1))
                        .map(Object::toString)
                        .map(String::trim)
                        .orElse(null));

                param.setCluster(Optional.ofNullable(getCellValue(row, 2))
                        .map(Object::toString)
                        .map(String::trim)
                        .orElse(null));

                param.setVariable(Optional.ofNullable(getCellValue(row, 3))
                        .map(Object::toString)
                        .map(String::trim)
                        .orElse(null));

                param.setOperator(Optional.ofNullable(getCellValue(row, 4))
                        .map(Object::toString)
                        .map(String::trim)
                        .orElse(null));

                param.setEnabled(Optional.ofNullable(getCellValue(row, 5))
                        .map(obj -> {
                            if (obj instanceof Boolean b) return b;
                            if (obj instanceof String s) return Boolean.parseBoolean(s.trim());
                            if (obj instanceof Double d) return d != 0;
                            return false;
                        })
                        .orElse(false));

                parameters.add(param);
            }
        }
    }
}

