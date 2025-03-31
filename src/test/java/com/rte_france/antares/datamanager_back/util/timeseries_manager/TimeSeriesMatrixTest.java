package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeSeriesMatrixTest {

    @Test
    void testConstructorAndGetters() {
        var column = new TimeSeriesMatrixColumn("column1", new double[]{1.0, 2.0, 3.0});
        var matrix = new TimeSeriesMatrix(List.of(column));

        assertEquals(1, matrix.columns().size());
        assertEquals(column, matrix.columns().get(0));
    }

    @Test
    void testGetRowCountEmptyColumns() {
        var matrix = new TimeSeriesMatrix(List.of());

        assertEquals(0, matrix.getRowCount());
    }
}