package com.rte_france.antares.datamanager_back.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@EnableCaching
public class AntaresDataManagerProperties {

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

    @Value("${antares.datamanager.sts.directory}")
    public String stsDirectory;

    @Value("${antares.datamanager.dsr.cluster.directory}")
    public String dsrDirectory;

    @Value("${antares.datamanager.dsr.capacity.modulation.directory}")
    public String dsrCapacityDirectory;

    @Value("${antares.datamanager.load.output.directory}")
    public String outputLoadDirectory;

    @Value("${antares.datamanager.thermal.modulation.output.directory}")
    public String paramModulationOutputDirectory;

    @Value("${antares.datamanager.dsr.ts.output.directory}")
    public String dsrModulationTsOutputDirectory;

    @Value("${antares.datamanager.misc.ts.output.directory}")
    public String miscGenTsOutputDirectory;

    @Value("${antares.datamanager.study.json.output.directory}")
    public String studyJsonOutputDirectory;

    @Value("${pegase.nas.directory}")
    public String nasDirectory;

    @Value("${peagse.genarator.host.url}")
    public String generatorHostUrl;

    @Value("${antares.datamanager.sts.ts.output.directory}")
    public String stsTsOutputDirectory;

    @Value("${antares.datamanager.misc.capacity.directory}")
    public String miscCapacityDirectory;

    @Value("${antares.datamanager.misc.load.directory}")
    public String miscLoadDirectory;

    @Value("${antares.datamanager.res.capacity.directory}")
    public String resCapacityDirectory;

    @Value("${antares.datamanager.res.load.directory}")
    public String resLoadDirectory;

    @Value("${antares.datamanager.res.distribution.directory}")
    public String resDistributionDirectory;

    @Value("${antares.datamanager.res.ts.output.directory}")
    public String resTsOutputDirectory;

    @Value("${antares.datamanager.hydro.series.directory}")
    public String hydroSeriesDirectory;

    @Value("${antares.datamanager.hydro.parameters.directory}")
    public String hydroParametersDirectory;

    @Value("${antares.datamanager.hydro.ts.output.directory}")
    public String hydroTsOutputDirectory;

    @Value("${antares.datamanager.psp.series.directory}")
    public String pspSeriesDirectory;

    @Value("${antares.datamanager.psp.parameters.directory}")
    public String pspParametersDirectory;

    @Value("${antares.datamanager.nuclear.modulation.directory}")
    public String nuclearModulationDirectory;

    @Value("${antares.datamanager.nuclear.talon.directory}")
    public String nuclearTalonDirectory;

    @Value("${antares.datamanager.nuclear.epr.directory}")
    public String nuclearEprDirectory;

    @Value("${antares.datamanager.nuclear.lt.directory}")
    public String nuclearLtDirectory;

    @Value("${antares.datamanager.nuclear.smr.directory}")
    public String nuclearSmrDirectory;

    @Value("${antares.datamanager.settings.directory}")
    public String trajectorySettingsDirectory;

    @Value("${antares.datamanager.nuclear.modulation.ts.output.directory}")
    public String nuclearModulationTsOutputDirectory;

    @Value("${antares.datamanager.nuclear.talon.ts.output.directory}")
    public String nuclearTalonTsOutputDirectory;

    @Value("${antares.datamanager.adequacy.directory}")
    public String adequacyDirectory;
}
