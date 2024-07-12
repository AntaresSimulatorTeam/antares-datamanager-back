package com.rte_france.antares.datamanager_back.configuration;

import com.rte_france.antares.datamanager_back.service.AreaFileProcessorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.messaging.MessageChannel;

import java.io.File;

@Configuration
@EnableIntegration
public class FileIntegrationConfig {

    private static final String AREA_INPUT_DIR = "src/main/resources/INPUT/areas";
    private static final String AREA_FILE_NAME_PATTERN = "areas_BP*.xlsx";

    @Bean
    public MessageChannel fileInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel fileOutputChannel() {
        return new DirectChannel();
    }

    @Bean
    public FileReadingMessageSource areaReadingFile() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(new File(AREA_INPUT_DIR));
        source.setFilter(new SimplePatternFileListFilter(AREA_FILE_NAME_PATTERN));
        return source;
    }

    @Bean
    public IntegrationFlow areaIntegration(AreaFileProcessorService areaFileProcessorService) {
        return IntegrationFlow.from(areaReadingFile(), configurer -> configurer.poller(Pollers.fixedDelay(2000)))
                .channel(fileInputChannel())
                .handle(areaFileProcessorService, "processAreaFile")
                .get();
    }

}
