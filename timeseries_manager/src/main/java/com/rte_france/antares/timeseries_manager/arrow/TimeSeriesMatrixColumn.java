/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.timeseries_manager.arrow;

import java.util.Arrays;
import java.util.Objects;

public record TimeSeriesMatrixColumn(String name, double[] values) {
    public int getSize() {
      return values.length;
    }

    public TimeSeriesMatrixColumn {
        Objects.requireNonNull(name);
        Objects.requireNonNull(values);
    }

    public TimeSeriesMatrixColumn renamed(String newName) {
        return new TimeSeriesMatrixColumn(newName, values);
    }

    @Override
    public String toString() {
        return "MatrixColumn{" +
                "name='" + name + '\'' +
                ", values=" + Arrays.toString(values) +
                '}';
    }
}
