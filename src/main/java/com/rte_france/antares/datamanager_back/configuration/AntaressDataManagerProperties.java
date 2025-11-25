package com.rte_france.antares.datamanager_back.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@EnableCaching
public class  AntaressDataManagerProperties {

    @Value("${antares.datamanager.trajectory.file.path}")
    public String trajectoryFilePath;

    @Value("${antares.datamanager.area.directory}")
    public String areaDirectory;

    @Value("${antares.datamanager.link.directory}")
    public String linkDirectory;

    @Value("${antares.datamanager.thermal.cost.directory}")
    public String thermalCostDirectory;

    @Value("${antares.datamanager.thermal.economic.directory}")
    public String thermalEconomicDirectory;

    @Value("${antares.datamanager.thermal.capacity.directory}")
    public String thermalCapacityDirectory;

    @Value("${antares.datamanager.thermal.parameter.directory}")
    public String thermalParameterDirectory;

    @Value("${antares.datamanager.thermal.parameter.modulation.directory}")
    public String thermalModulationParameterDirectory;


    @Value("${antares.datamanager.load.directory}")
    public String loadDirectory;

    @Value("${antares.datamanager.load.output.directory}")
    public String outputLoadDirectory;

    @Value("${antares.datamanager.thermal.modulation.output.directory}")
    public String paramModulationOutputDirectory;

    @Value("${antares.datamanager.study.json.output.directory}")
    public String studyJsonOutputDirectory;

    @Value("${pegase.nas.directory}")
    public String nasDirectory;

    @Value("${peagse.genarator.host.url}")
    public String generatorHostUrl;

    @Value("antares.datamanager.thermal.modulation.tmp.output.directory")
    public String paramModulationTmpOutputDirectory;

}
