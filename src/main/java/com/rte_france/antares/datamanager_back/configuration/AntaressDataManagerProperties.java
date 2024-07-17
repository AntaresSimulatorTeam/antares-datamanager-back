package com.rte_france.antares.datamanager_back.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@EnableCaching
public class AntaressDataManagerProperties {
    @Value("${trajectory.file.path}")
    public String trajectoryFilePath;}
