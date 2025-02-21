package com.rte_france.antares.datamanager_back.configuration;

import com.rte_france.antares.timeseries_manager.main.ArrowTSReader;
import com.rte_france.antares.timeseries_manager.main.ArrowTSWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeSeriesConfig {
    @Bean
    public ArrowTSReader arrowTSReader() {
        return new ArrowTSReader();
    }

    @Bean
    public ArrowTSWriter arrowTSWriter() {
        return new ArrowTSWriter();
    }
}