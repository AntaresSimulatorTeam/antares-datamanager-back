package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimeSeriesMatrixColumnTest {

    @Test
    void testConstructorAndGetters() {
        double[] values = {1.0, 2.0, 3.0};
        var column = new TimeSeriesMatrixColumn("column1", values);

        assertEquals("column1", column.name());
        assertArrayEquals(values, column.values());
    }

    @Test
    void testSize() {
        double[] values = {1.0, 2.0, 3.0};
        var column = new TimeSeriesMatrixColumn("column1", values);

        assertEquals(3, column.size());
    }

    @Test
    void testEqualsAndHashCode() {
        double[] values1 = {1.0, 2.0, 3.0};
        double[] values2 = {1.0, 2.0, 3.0};
        var column1 = new TimeSeriesMatrixColumn("column1", values1);
        var column2 = new TimeSeriesMatrixColumn("column1", values2);

        assertEquals(column1, column2);
        assertEquals(column1.hashCode(), column2.hashCode());
    }

    @Test
    void testToString() {
        double[] values = {1.0, 2.0, 3.0};
        var column = new TimeSeriesMatrixColumn("column1", values);

        var expected = "MatrixColumn{name='column1', values=[1.0, 2.0, 3.0]}";
        assertEquals(expected, column.toString());
    }
}