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

    public record ParsedRestrictions(List<RestrictionRow> constraints, Map<String, List<String>> linkLists) {
    }

    public record RestrictionRow(String name, boolean enabled, String sign, String temporality,
                                 String type, String listLinks, String secondMember) {
    }

    public static ParsedRestrictions parse(Workbook workbook, String horizon, String fileName) {
        if (horizon == null || !horizon.matches("[0-9]{4}(-[0-9]{4})?")) {
            throw invalid(fileName, String.valueOf(horizon), 0, 0, "Expected horizon YYYY or YYYY-YYYY");
        }
        String year = horizon.substring(horizon.length() - 4);
        if (workbook == null) {
            throw invalid(fileName, year, 0, 0, "Workbook is required");
        }
        var parser = new FlowbasedConstraintsRestrictionsValidator(workbook, fileName);
        Sheet constraintsSheet = parser.requiredSheet(workbook, year);
        Sheet listsSheet = parser.requiredSheet(workbook, LIST_LINKS_SHEET);
        parser.validateHeaders(constraintsSheet);
        Map<String, List<String>> allLists = parser.readLists(listsSheet);
        Map<String, List<String>> referencedLists = new LinkedHashMap<>();
        List<RestrictionRow> constraints = parser.readConstraints(constraintsSheet, allLists, referencedLists);
        return new ParsedRestrictions(List.copyOf(constraints), Collections.unmodifiableMap(referencedLists));
    }

    private Sheet requiredSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null || !sheet.getSheetName().equals(name)) {
            throw invalid(fileName, name, 0, 0, "Required sheet is missing");
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

    private List<RestrictionRow> readConstraints(Sheet sheet, Map<String, List<String>> allLists,
                                               Map<String, List<String>> referencedLists) {
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
            List<String> links = allLists.get(reference);
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

    private Map<String, List<String>> readLists(Sheet sheet) {
        Map<Integer, String> names = new LinkedHashMap<>();
        Map<String, List<String>> lists = new LinkedHashMap<>();
        Row header = sheet.getRow(0);
        if (header == null) {
            throw invalid(fileName, sheet.getSheetName(), 0, 0, "List header row is missing");
        }
        for (Cell cell : header) {
            String name = text(cell, sheet, 0, cell.getColumnIndex());
            if (name != null) {
                if (lists.putIfAbsent(name, new ArrayList<>()) != null) {
                    throw invalid(fileName, sheet.getSheetName(), 0, cell.getColumnIndex(),
                            "Duplicate list header: " + name);
                }
                names.put(cell.getColumnIndex(), name);
            }
        }
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            for (Cell cell : row) {
                String value = text(cell, sheet, row.getRowNum(), cell.getColumnIndex());
                if (value == null) {
                    continue;
                }
                String name = names.get(cell.getColumnIndex());
                if (name == null) {
                    throw invalid(fileName, sheet.getSheetName(), row.getRowNum(), cell.getColumnIndex(),
                            "Data under an unnamed list column");
                }
                lists.get(name).add(value);
            }
        }
        return lists;
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
}
