/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.datamanager_back.util;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author Sylvain Leclerc <sylvain.leclerc@rte-france.com>
 */
public record MatrixColumn(String name, ColumnType type, int[] intValues, float[] floatValues) {

  public float[] getFloatValues() {
        if (type != ColumnType.FLOAT) {
            throw new IllegalArgumentException("Cannot get float values from int column");
        }
        return floatValues;
    }

    public int[] getIntValues() {
        if (type != ColumnType.INT) {
            throw new IllegalArgumentException("Cannot get int values from float column");
        }
        return intValues;
    }

    public int getSize() {
      return switch (type) {
        case INT -> intValues.length;
        case FLOAT -> floatValues.length;
        default -> throw new IllegalStateException("Invalid column type " + type);
      };
    }

    public MatrixColumn(String name, float[] values) {
        this(name, ColumnType.FLOAT, null, Objects.requireNonNull(values.clone()));
    }

    public MatrixColumn(String name, int[] values) {
        this(name, ColumnType.INT, Objects.requireNonNull(values.clone()), null);
    }

    public MatrixColumn {
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        if (type == ColumnType.FLOAT && floatValues == null) {
            throw new IllegalArgumentException("Float column must have floatValues");
        }
        if (type == ColumnType.INT && intValues == null) {
            throw new IllegalArgumentException("Int column must have intValues");
        }
    }

    public MatrixColumn renamed(String newName) {
        return new MatrixColumn(newName, type, intValues, floatValues);
    }

    @Override
    public String toString() {
        return "MatrixColumn{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", floatValues=" + Arrays.toString(floatValues) +
                ", intValues=" + Arrays.toString(intValues) +
                '}';
    }
}
