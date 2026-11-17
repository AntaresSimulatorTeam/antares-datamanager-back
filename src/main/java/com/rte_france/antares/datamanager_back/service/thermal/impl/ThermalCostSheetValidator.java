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
        List<String> names = readHeaderNames(header);
        boolean countryFirst = !names.isEmpty() && "country".equals(names.get(0));
        int offset = countryFirst ? 1 : 0;
        validateHeaders(names, offset, trajectoryName);
        validateDataRows(sheet, countryFirst, offset, trajectoryName);
        return offset;
    }

    private static List<String> readHeaderNames(Row header) {
        int lastCell = header == null ? 0 : header.getLastCellNum();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < lastCell; i++) {
            String name = headerValue(header.getCell(i));
            names.add(name);
        }
        return names;
    }

    private static void validateHeaders(List<String> names, int offset, String trajectoryName) {
        int lastNonBlank = lastNonBlankIndex(names);
        int yearStart = offset + REQUIRED.size();
        validateRequiredHeaders(names, offset, yearStart, lastNonBlank, trajectoryName);
        validateUnexpectedHeaders(names, yearStart, lastNonBlank, trajectoryName);
    }

    private static void validateRequiredHeaders(
            List<String> names, int offset, int yearStart, int lastNonBlank, String trajectoryName) {
        Set<String> missing = new LinkedHashSet<>();
        if (names.lastIndexOf("country") > 0) {
            missing.add("country");
        }
        addMissingRequiredHeaders(names, offset, missing);
        if (!containsYear(names, yearStart, lastNonBlank)) {
            missing.add("YYYY");
        }
        if (!missing.isEmpty()) {
            throw badRequest("Missing columns {0} in Thermal Costs trajectory {1}",
                    String.join(", ", missing), trajectoryName);
        }
    }

    private static void addMissingRequiredHeaders(List<String> names, int offset, Set<String> missing) {
        for (int i = 0; i < REQUIRED.size(); i++) {
            int position = i + offset;
            String expected = REQUIRED.get(i).getDisplayName();
            if (position >= names.size() || !expected.equals(names.get(position))
                    || names.lastIndexOf(expected) > position) {
                missing.add(expected);
            }
        }
    }

    private static boolean containsYear(List<String> names, int yearStart, int lastNonBlank) {
        for (int i = yearStart; i <= lastNonBlank; i++) {
            if (isYear(names.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static void validateUnexpectedHeaders(
            List<String> names, int yearStart, int lastNonBlank, String trajectoryName) {
        Set<String> unexpected = new LinkedHashSet<>();
        for (int i = 0; i <= lastNonBlank; i++) {
            String name = names.get(i);
            if (name == null) {
                unexpected.add("<empty at " + CellReference.convertNumToColString(i) + ">");
            } else if (i >= yearStart && (!isYear(name) || isDuplicateYear(names, yearStart, i, name))) {
                unexpected.add(name);
            }
        }
        if (!unexpected.isEmpty()) {
            throw badRequest("Unexpected columns {0} in Thermal Costs trajectory {1}",
                    String.join(", ", unexpected), trajectoryName);
        }
    }

    private static boolean isDuplicateYear(List<String> names, int yearStart, int index, String name) {
        return isYear(name) && names.subList(yearStart, index).contains(name);
    }

    private static int lastNonBlankIndex(List<String> names) {
        for (int i = names.size() - 1; i >= 0; i--) {
            if (names.get(i) != null) {
                return i;
            }
        }
        return -1;
    }

    private static void validateDataRows(Sheet sheet, boolean countryFirst, int offset, String trajectoryName) {
        DataValidationErrors errors = new DataValidationErrors();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                collectRowErrors(row, countryFirst, offset, errors);
            }
        }
        throwDataValidationError(errors, trajectoryName);
    }

    private static void collectRowErrors(Row row, boolean countryFirst, int offset, DataValidationErrors errors) {
        if (countryFirst && exceedsLength(getCellValue(row, ThermalEconomicCostColumns.COUNTRY.getIndex()), 10)) {
            errors.countryTooLong = true;
        }
        for (ThermalEconomicCostColumns column : SHORT_COLUMNS) {
            Object value = getCellValue(row, column.indexWithCountryOffset(offset));
            if (exceedsLength(value, 20)) {
                errors.tooLong.add(column.getDisplayName());
            }
        }
        Object comment = getCellValue(row, ThermalEconomicCostColumns.COMMENT.indexWithCountryOffset(offset));
        if (exceedsLength(comment, 255)) {
            errors.commentTooLong = true;
        }
        if (hasInvalidRatio(row, offset)) {
            errors.invalidRatio = true;
        }
    }

    private static boolean hasInvalidRatio(Row row, int offset) {
        int ratioIndex = ThermalEconomicCostColumns.RATIO.indexWithCountryOffset(offset);
        Cell ratioCell = row.getCell(ratioIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        Object ratio = getCellValue(ratioCell);
        if (ratio == null) {
            return ratioCell != null && ratioCell.getCellType() != CellType.BLANK;
        }
        if (ratio instanceof String text && text.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(String.valueOf(ratio));
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static void throwDataValidationError(DataValidationErrors errors, String trajectoryName) {
        if (!errors.tooLong.isEmpty()) {
            throw badRequest("Columns {0} value cannot exceed 20 characters in Thermal Costs trajectory {1}",
                    String.join(", ", errors.tooLong), trajectoryName);
        }
        if (errors.countryTooLong) {
            throw badRequest("Column country value cannot exceed 10 characters in Thermal Costs trajectory {0}",
                    trajectoryName);
        }
        if (errors.commentTooLong) {
            throw badRequest("Column comment value cannot exceed 255 characters in Thermal Costs trajectory {0}",
                    trajectoryName);
        }
        if (errors.invalidRatio) {
            throw badRequest("Column ratio_ncv_hcv must be numeric in Thermal Costs trajectory {0}",
                    trajectoryName);
        }
    }

    private static final class DataValidationErrors {
        Set<String> tooLong = new LinkedHashSet<>();
        boolean countryTooLong;
        boolean commentTooLong;
        boolean invalidRatio;
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
