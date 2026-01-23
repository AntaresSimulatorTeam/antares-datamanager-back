package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.text.Normalizer;
import java.util.*;

@Getter
public enum ExcelFileType {
    AREAS(9, AreaColumns.class),
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
                .toList();
    }

    public static String normalizeColumnName(String columnName) {
        if (columnName == null) return null;

        return Normalizer.normalize(columnName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "") // Remove diacritics (accents)
                .trim();
    }
}