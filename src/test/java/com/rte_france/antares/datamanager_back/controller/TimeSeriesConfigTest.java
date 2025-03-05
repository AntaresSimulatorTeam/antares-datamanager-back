package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.configuration.TimeSeriesConfig;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TimeSeriesConfigTest {
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void setUp() {
        context = new AnnotationConfigApplicationContext(TimeSeriesConfig.class);
    }

    @AfterAll
    static void tearDown() {
        context.close();
    }

    @Test
    void timeSeriesReaderBeanShouldBeCreated() {
        var timeSeriesReader = context.getBean(TimeSeriesReader.class);
        assertNotNull(timeSeriesReader, "TimeSeriesReader bean should be created");
    }

    @Test
    void timeSeriesWriterBeanShouldBeCreated() {
        var timeSeriesWriter = context.getBean(TimeSeriesWriter.class);
        assertNotNull(timeSeriesWriter, "TimeSeriesWriter bean should be created");
    }
}