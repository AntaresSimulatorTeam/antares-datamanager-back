/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.timeseries_manager.structures;

/**
 * This class was made for testing purposes with a fully primitive data structure to see if Arrow speeds could be made
 * better. It was not the case
 */
public final class TimeSeriesMatrixPrimitive {
    private static final int MAX_ROWS = 8784;
    private final double[][] matrix;
    private final int totalCols;
    private int colCount;
    private int rowCount;

    public TimeSeriesMatrixPrimitive(int totalCols) {
        if (totalCols <= 0) {
            throw new IllegalArgumentException("totalCols <= 0");
        }
        this.totalCols = totalCols;
        this.matrix = new double[totalCols][MAX_ROWS];
    }

    public void add(double value) {
        if (colCount >= totalCols) {
            throw new IllegalStateException("Matrix is full");
        }
        matrix[colCount][rowCount++] = value;
        if (rowCount >= MAX_ROWS) {
            rowCount = 0;
            colCount++;
        }
    }

    public double[][] columns() {
        return matrix.clone();
    }

    public int rowCount() {
        return rowCount;
    }

    public int colCount() {
        return colCount;
    }
}
