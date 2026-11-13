package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ThermalEconomicCostColumns;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.rte_france.antares.datamanager_back.util.Utils.getCellValue;

final class ThermalCostSheetValidator {
    private static final List<ThermalEconomicCostColumns> REQUIRED = List.of(
            ThermalEconomicCostColumns.FUEL, ThermalEconomicCostColumns.COMMENT,
            ThermalEconomicCostColumns.UNIT, ThermalEconomicCostColumns.MODULATION,
            ThermalEconomicCostColumns.RATIO);
    private static final List<ThermalEconomicCostColumns> SHORT_COLUMNS = List.of(
            ThermalEconomicCostColumns.FUEL, ThermalEconomicCostColumns.UNIT,
            ThermalEconomicCostColumns.MODULATION);

    private ThermalCostSheetValidator() {
    }

    static int validate(Sheet sheet, String trajectoryName) {
        Row header = sheet.getRow(0);
        int lastCell = header == null ? 0 : header.getLastCellNum();
        int lastNonBlank = -1;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < lastCell; i++) {
            String name = headerValue(header.getCell(i));
            names.add(name);
            if (name != null) {
                lastNonBlank = i;
            }
        }
        boolean countryFirst = !names.isEmpty() && "country".equals(names.get(0));
        int offset = countryFirst ? 1 : 0;
        Set<String> missing = new LinkedHashSet<>();
        if (names.lastIndexOf("country") > 0) {
            missing.add("country");
        }
        for (int i = 0; i < REQUIRED.size(); i++) {
            int position = i + offset;
            String expected = REQUIRED.get(i).getDisplayName();
            if (position >= names.size() || !expected.equals(names.get(position))) {
                missing.add(expected);
            }
            if (names.lastIndexOf(expected) > position) {
                missing.add(expected);
            }
        }
        int yearStart = offset + REQUIRED.size();
        boolean hasYear = false;
        for (int i = yearStart; i <= lastNonBlank; i++) {
            if (isYear(names.get(i))) {
                hasYear = true;
            }
        }
        if (!hasYear) {
            missing.add("YYYY");
        }
        if (!missing.isEmpty()) {
            throw badRequest("Missing columns {0} in Thermal Costs trajectory {1}",
                    String.join(", ", missing), trajectoryName);
        }

        Set<String> unexpected = new LinkedHashSet<>();
        for (int i = 0; i <= lastNonBlank; i++) {
            String name = names.get(i);
            if (name == null) {
                unexpected.add("<empty at " + CellReference.convertNumToColString(i) + ">");
            } else if (i >= yearStart && !isYear(name)) {
                unexpected.add(name);
            } else if (i >= yearStart && isYear(name) && names.subList(yearStart, i).contains(name)) {
                unexpected.add(name);
            }
        }
        if (!unexpected.isEmpty()) {
            throw badRequest("Unexpected columns {0} in Thermal Costs trajectory {1}",
                    String.join(", ", unexpected), trajectoryName);
        }

        Set<String> tooLong = new LinkedHashSet<>();
        boolean countryTooLong = false;
        boolean commentTooLong = false;
        boolean invalidRatio = false;
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            if (countryFirst && exceedsLength(getCellValue(row, ThermalEconomicCostColumns.COUNTRY.getIndex()), 10)) {
                countryTooLong = true;
            }
            for (ThermalEconomicCostColumns column : SHORT_COLUMNS) {
                Object value = getCellValue(row, column.indexWithCountryOffset(offset));
                if (exceedsLength(value, 20)) {
                    tooLong.add(column.getDisplayName());
                }
            }
            Object comment = getCellValue(row, ThermalEconomicCostColumns.COMMENT.indexWithCountryOffset(offset));
            if (exceedsLength(comment, 255)) {
                commentTooLong = true;
            }
            int ratioIndex = ThermalEconomicCostColumns.RATIO.indexWithCountryOffset(offset);
            Cell ratioCell = row.getCell(ratioIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            Object ratio = getCellValue(ratioCell);
            if (ratio == null) {
                if (ratioCell != null && ratioCell.getCellType() != CellType.BLANK) {
                    invalidRatio = true;
                }
            } else if (!(ratio instanceof String text && text.isBlank())) {
                try {
                    new BigDecimal(String.valueOf(ratio));
                } catch (NumberFormatException e) {
                    invalidRatio = true;
                }
            }
        }
        if (!tooLong.isEmpty()) {
            throw badRequest("Columns {0} value cannot exceed 20 characters in Thermal Costs trajectory {1}",
                    String.join(", ", tooLong), trajectoryName);
        }
        if (countryTooLong) {
            throw badRequest("Column country value cannot exceed 10 characters in Thermal Costs trajectory {0}",
                    trajectoryName);
        }
        if (commentTooLong) {
            throw badRequest("Column comment value cannot exceed 255 characters in Thermal Costs trajectory {0}",
                    trajectoryName);
        }
        if (invalidRatio) {
            throw badRequest("Column ratio_ncv_hcv must be numeric in Thermal Costs trajectory {0}",
                    trajectoryName);
        }
        return offset;
    }

    private static String headerValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue().trim();
            return value.isEmpty() ? null : value.toLowerCase(Locale.ROOT);
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            return value == Math.floor(value) && value >= 1000 && value <= 9999
                    ? Integer.toString((int) value) : Double.toString(value);
        }
        if (cell.getCellType() == CellType.BLANK) {
            return null;
        }
        return cell.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isYear(String value) {
        return value != null && value.matches("[0-9]{4}");
    }

    private static boolean exceedsLength(Object value, int limit) {
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value);
        return text.codePointCount(0, text.length()) > limit;
    }

    private static BusinessException badRequest(String message, String... arguments) {
        return BusinessException.builder()
                .message(message)
                .errorMessageArguments(List.of(arguments))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }
}
