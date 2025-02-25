/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import java.util.Arrays;
import java.util.Objects;

public record TimeSeriesMatrixColumn(String name, double[] values) {
    public int size() {
      return values.length;
    }

    public TimeSeriesMatrixColumn {
        Objects.requireNonNull(name);
        Objects.requireNonNull(values);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var column = (TimeSeriesMatrixColumn) o;
        return name.equals(column.name()) && Arrays.equals(values, column.values());
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(name);
        result = 31 * result + Arrays.hashCode(values);
        return result;
    }

    @Override
    public String toString() {
        return "MatrixColumn{" +
                "name='" + name + '\'' +
                ", values=" + Arrays.toString(values) +
                '}';
    }
}