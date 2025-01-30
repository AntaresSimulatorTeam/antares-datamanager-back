package com.rte_france.antares.datamanager_back.util;

import org.apache.parquet.io.api.Converter;
import org.apache.parquet.io.api.GroupConverter;
import org.apache.parquet.io.api.PrimitiveConverter;
import org.apache.parquet.schema.GroupType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MatrixGroupConverter extends GroupConverter {
    private final List<MatrixColumn> columns = new ArrayList<>();
    private final List<String> columnNames;
    private final int rowCount;

    public MatrixGroupConverter(GroupType schema, int rowCount) {
        Objects.requireNonNull(schema);
        this.rowCount = Objects.checkIndex(rowCount, 8761);
        this.columnNames = new ArrayList<>();
        for (var field : schema.getFields()) {
            columnNames.add(field.getName());
        }
    }

    @Override
    public Converter getConverter(int fieldIndex) {
        return new PrimitiveConverter() {
            private final double[] values = new double[rowCount];
            private int currentIndex = 0;

            @Override
            public void addDouble(double value) {
                values[currentIndex++] = value;
            }

            @Override
            public void addFloat(float value) {
                values[currentIndex++] = value;
            }

            @Override
            public void addInt(int value) {
                values[currentIndex++] = value;
            }

            @Override
            public void addLong(long value) {
                values[currentIndex++] = value;
            }

            @Override
            public void addBoolean(boolean value) {
                values[currentIndex++] = value ? 1.0 : 0.0;
            }
        };
    }

    @Override
    public void start() {}

    @Override
    public void end() {
      for (var name : columnNames) {
        double[] values = new double[rowCount];
        columns.add(new MatrixColumn(name, values));
      }
    }

    public Matrix getMatrix() {
        return new Matrix(columns);
    }
}
