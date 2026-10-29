package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.FlowbasedConstraintsRestrictionsColumns;
import org.apache.poi.ss.formula.eval.NotImplementedException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.FlowbasedConstraintsRestrictionsColumns.*;

public final class FlowbasedConstraintsRestrictionsValidator {
    private static final String LIST_LINKS_SHEET = "listLinks";
    private final String fileName;
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);
    private final FormulaEvaluator evaluator;

    private FlowbasedConstraintsRestrictionsValidator(Workbook workbook, String fileName) {
        this.fileName = fileName;
        this.evaluator = workbook.getCreationHelper().createFormulaEvaluator();
    }

    public record WeightedLink(String name, double weight) {
    }

    public record ParsedRestrictions(List<RestrictionRow> constraints, Map<String, List<WeightedLink>> linkLists) {
    }

    public record RestrictionRow(String name, boolean enabled, String operator, String type,
                                 String constraintType, String listLinks, String secondMember) {
    }

    public static ParsedRestrictions parse(Workbook workbook, String horizon, String fileName, String trajectoryName) {
        if (horizon == null || !horizon.matches("[0-9]{4}(-[0-9]{4})?")) {
            throw invalid(fileName, String.valueOf(horizon), 0, 0, "Expected horizon YYYY or YYYY-YYYY");
        }
        String year = java.util.Arrays.stream(horizon.split("-")).max(String::compareTo).orElseThrow();
        if (workbook == null) {
            throw invalid(fileName, year, 0, 0, "Workbook is required");
        }
        var parser = new FlowbasedConstraintsRestrictionsValidator(workbook, fileName);
        Sheet listsSheet = parser.requiredSheet("Missing required sheet", workbook, LIST_LINKS_SHEET, trajectoryName);
        Sheet constraintsSheet = parser.requiredSheet("Missing horizon", workbook, horizon, trajectoryName);
        parser.validateHeaders(constraintsSheet);
        Map<String, List<WeightedLink>> allLists = parser.readLists(listsSheet);
        Map<String, List<WeightedLink>> referencedLists = new LinkedHashMap<>();
        List<RestrictionRow> constraints = parser.readConstraints(constraintsSheet, allLists, referencedLists);
        return new ParsedRestrictions(List.copyOf(constraints), Collections.unmodifiableMap(referencedLists));
    }

    private Sheet requiredSheet(String reason, Workbook workbook, String name, String trajectoryName) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null || !sheet.getSheetName().equals(name)) {
            throw invalidSheet(reason, name, trajectoryName, fileName);
        }
        return sheet;
    }

    private void validateHeaders(Sheet sheet) {
        Row header = sheet.getRow(0);
        for (FlowbasedConstraintsRestrictionsColumns column : FlowbasedConstraintsRestrictionsColumns.values()) {
            String actual = text(header == null ? null : header.getCell(column.ordinal()), sheet, 0, column.ordinal());
            if (!column.getDisplayName().equalsIgnoreCase(actual)) {
                throw invalid(fileName, sheet.getSheetName(), 0, column.ordinal(),
                        "Expected header " + column.getDisplayName() + ", found " + actual);
            }
        }
        rejectExtraCells(header, sheet);
    }

    private List<RestrictionRow> readConstraints(Sheet sheet, Map<String, List<WeightedLink>> allLists,
                                                 Map<String, List<WeightedLink>> referencedLists) {
        List<RestrictionRow> constraints = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            rejectExtraCells(row, sheet);
            String[] fields = new String[FlowbasedConstraintsRestrictionsColumns.values().length];
            boolean populated = false;
            for (int column = 0; column < fields.length; column++) {
                fields[column] = text(row.getCell(column), sheet, row.getRowNum(), column);
                populated |= fields[column] != null;
            }
            if (!populated) {
                continue;
            }
            require(fields, NOM, sheet, row);
            require(fields, LIST_LINKS, sheet, row);
            String enabled = fields[ENABLED.ordinal()];
            if (!"YES".equalsIgnoreCase(enabled) && !"NO".equalsIgnoreCase(enabled)) {
                throw invalid(fileName, sheet.getSheetName(), row.getRowNum(), ENABLED.ordinal(),
                        "enabled must be YES or NO, found " + enabled);
            }
            String reference = fields[LIST_LINKS.ordinal()];
            List<WeightedLink> links = allLists.get(reference);
            if (links == null || links.isEmpty()) {
                throw invalid(fileName, sheet.getSheetName(), row.getRowNum(), LIST_LINKS.ordinal(),
                        "Referenced list is absent or empty in listLinks: " + reference);
            }
            referencedLists.computeIfAbsent(reference, ignored -> List.copyOf(links));
            constraints.add(new RestrictionRow(fields[NOM.ordinal()], "YES".equalsIgnoreCase(enabled),
                    fields[SIGNE.ordinal()], fields[TEMPORALITY.ordinal()], fields[TYPE.ordinal()],
                    fields[LIST_LINKS.ordinal()], fields[SECOND_MEMBER.ordinal()]));
        }
        return constraints;
    }

    private void require(String[] fields, FlowbasedConstraintsRestrictionsColumns column, Sheet sheet, Row row) {
        if (fields[column.ordinal()] == null) {
            throw invalid(fileName, sheet.getSheetName(), row.getRowNum(), column.ordinal(),
                    "Required value is missing: " + column.getDisplayName());
        }
    }

    private void rejectExtraCells(Row row, Sheet sheet) {
        for (Cell cell : row) {
            if (cell.getColumnIndex() >= FlowbasedConstraintsRestrictionsColumns.values().length
                    && text(cell, sheet, row.getRowNum(), cell.getColumnIndex()) != null) {
                throw invalid(fileName, sheet.getSheetName(), row.getRowNum(), cell.getColumnIndex(),
                        "Unexpected populated column after second_member");
            }
        }
    }

    private Map<String, List<WeightedLink>> readLists(Sheet sheet) {
        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, Integer> weights = new LinkedHashMap<>();
        Map<Integer, String> weightColumns = new LinkedHashMap<>();
        Map<String, List<WeightedLink>> lists = new LinkedHashMap<>();
        Row header = sheet.getRow(0);
        if (header == null) {
            throw invalid(fileName, sheet.getSheetName(), 0, 0, "List header row is missing");
        }
        int width = maxColumnCount(sheet);
        for (int column = 0; column < width; column++) {
            String name = text(header.getCell(column), sheet, 0, column);
            if (name == null) {
                continue;
            }
            if ("poids".equalsIgnoreCase(name)) {
                throw invalid(fileName, sheet.getSheetName(), 0, column,
                        "Weight header has no list name immediately before it");
            }
            String weightHeader = text(header.getCell(column + 1), sheet, 0, column + 1);
            if (weightHeader == null || !"poids".equalsIgnoreCase(weightHeader)) {
                throw invalid(fileName, sheet.getSheetName(), 0, column + 1,
                        "Expected poids header immediately after list: " + name);
            }
            if (lists.putIfAbsent(name, new ArrayList<>()) != null) {
                throw invalid(fileName, sheet.getSheetName(), 0, column, "Duplicate list header: " + name);
            }
            names.put(column, name);
            weights.put(column, column + 1);
            weightColumns.put(column + 1, name);
            column++;
        }
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            for (int column = 0; column < width; column++) {
                if (weightColumns.containsKey(column)) {
                    continue;
                }
                String name = names.get(column);
                Cell linkCell = row.getCell(column);
                String link = text(linkCell, sheet, row.getRowNum(), column);
                int weightColumn = weights.getOrDefault(column, -1);
                Double weight = weightColumn < 0 ? null
                        : numericWeight(row.getCell(weightColumn), sheet, row.getRowNum(), weightColumn);
                if (name == null) {
                    if (link != null || hasNumericValue(linkCell, sheet, row.getRowNum(), column)) {
                        throw invalid(fileName, sheet.getSheetName(), row.getRowNum(), column,
                                "Data under an unnamed list column");
                    }
                    continue;
                }
                if (link == null && weight == null) {
                    continue;
                }
                if (link == null) {
                    throw invalid(fileName, sheet.getSheetName(), row.getRowNum(), weightColumn,
                            "Weight has no link");
                }
                if (weight == null) {
                    throw invalid(fileName, sheet.getSheetName(), row.getRowNum(), weightColumn,
                            "Link has no weight");
                }
                lists.get(name).add(new WeightedLink(link, weight));
            }
        }
        return lists;
    }

    private boolean hasNumericValue(Cell cell, Sheet sheet, int row, int column) {
        if (cell == null) {
            return false;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return true;
        }
        if (cell.getCellType() == CellType.FORMULA) {
            CellValue result;
            try {
                result = evaluator.evaluate(cell);
            } catch (NotImplementedException | IllegalArgumentException | IllegalStateException e) {
                throw invalid(fileName, sheet.getSheetName(), row, column,
                        "Cannot evaluate formula: " + e.getMessage());
            }
            return result != null && result.getCellType() == CellType.NUMERIC;
        }
        return false;
    }

    private int maxColumnCount(Sheet sheet) {
        int width = 0;
        for (Row row : sheet) {
            width = Math.max(width, row.getLastCellNum());
        }
        return width;
    }

    private Double numericWeight(Cell cell, Sheet sheet, int row, int column) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        double weight;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            CellValue result;
            try {
                result = evaluator.evaluate(cell);
            } catch (NotImplementedException | IllegalArgumentException | IllegalStateException e) {
                throw invalid(fileName, sheet.getSheetName(), row, column,
                        "Cannot evaluate formula: " + e.getMessage());
            }
            if (result == null) {
                throw invalid(fileName, sheet.getSheetName(), row, column, "Formula has no result");
            }
            type = result.getCellType();
            if (type == CellType.NUMERIC) {
                weight = result.getNumberValue();
            } else if (type == CellType.STRING && result.getStringValue().trim().isEmpty()) {
                return null;
            } else {
                throw invalid(fileName, sheet.getSheetName(), row, column,
                        "Weight must be numeric, found " + type);
            }
        } else if (type == CellType.NUMERIC) {
            weight = cell.getNumericCellValue();
        } else if (type == CellType.STRING) {
            String value = cell.getStringCellValue().trim();
            if (value.isEmpty()) {
                return null;
            }
            try {
                weight = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw invalid(fileName, sheet.getSheetName(), row, column, "Weight is not numeric: " + value);
            }
        } else {
            throw invalid(fileName, sheet.getSheetName(), row, column,
                    "Weight must be numeric, found " + type);
        }
        if (!Double.isFinite(weight)) {
            throw invalid(fileName, sheet.getSheetName(), row, column, "Weight must be finite");
        }
        return weight;
    }

    private String text(Cell cell, Sheet sheet, int row, int column) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            CellValue result;
            try {
                result = evaluator.evaluate(cell);
            } catch (NotImplementedException | IllegalArgumentException | IllegalStateException e) {
                throw invalid(fileName, sheet.getSheetName(), row, column, "Cannot evaluate formula: " + e.getMessage());
            }
            if (result == null) {
                throw invalid(fileName, sheet.getSheetName(), row, column, "Formula has no result");
            }
            type = result.getCellType();
        }
        if (type == CellType.ERROR || type == CellType._NONE || type == CellType.FORMULA) {
            throw invalid(fileName, sheet.getSheetName(), row, column, "Unsupported or error cell type: " + type);
        }
        String value = formatter.formatCellValue(cell, evaluator).trim();
        return value.isEmpty() ? null : value;
    }

    private static BusinessException invalid(String fileName, String sheet, int row, int column, String reason) {
        return BusinessException.builder()
                .message("Invalid restrictions file {0}, sheet {1}, row {2}, column {3}: {4}")
                .errorMessageArguments(List.of(String.valueOf(fileName), sheet, Integer.toString(row + 1),
                        CellReference.convertNumToColString(column), reason))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private static BusinessException invalidSheet(String reason, String sheet, String trajectoryName, String fileName) {
        return BusinessException.builder()
                .message("{0} {1} in FLOWBASED {2} in file {3}")
                .errorMessageArguments(List.of(reason, sheet, trajectoryName, String.valueOf(fileName)))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }
}
