package com.rte_france.antares.datamanager_back.util.ExcelFileValidators.ColumnsEnums;

import lombok.Getter;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Getter
public enum ExcelFileType {
    AREAS(8, AreaColumns.class),
    LINKS(13, LinksColumns.class);

    private final int columnCount;
    private final List<String> columnNames;

    <T extends Enum<T>> ExcelFileType(int columnCount, Class<T> columnEnum) {
        this.columnCount = columnCount;
        this.columnNames = Arrays.stream(columnEnum.getEnumConstants())
                .map(e -> {
                    try {
                        return (String) e.getClass().getMethod("getDisplayName").invoke(e);
                    } catch (Exception ex) {
                        return e.name().replace('_', ' ');
                    }
                })
                .map(ExcelFileType::normalizeColumnName)
                .collect(Collectors.toList());
    }

    /**
     * @param actualColumns columns name from excel files
     * @return columns that do not match ColumnsName enums values
     */
    public List<String> checkColumnNames(List<String> actualColumns) {
        List<String> normalizedActual = actualColumns.stream()
                .map(ExcelFileType::normalizeColumnName)
                .toList();

        Set<String> missingColumns = new HashSet<>(columnNames);
        normalizedActual.forEach(missingColumns::remove);

        return new ArrayList<>(missingColumns);
    }
    private static String normalizeColumnName(String columnName) {
        if (columnName == null) return null;

        return Normalizer.normalize(columnName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "") // Remove diacritics (accents)
                .trim();
    }
}