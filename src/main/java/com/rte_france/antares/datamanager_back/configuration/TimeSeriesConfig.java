package com.rte_france.antares.datamanager_back.configuration;

import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeSeriesConfig {
    @Bean
    public TimeSeriesReader arrowTSReader() {
        return new TimeSeriesReader();
    }

    @Bean
    public TimeSeriesWriter arrowTSWriter() {
        return new TimeSeriesWriter();
    }
}