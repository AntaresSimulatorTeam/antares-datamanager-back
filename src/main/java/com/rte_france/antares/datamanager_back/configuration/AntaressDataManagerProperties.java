package com.rte_france.antares.datamanager_back.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@EnableCaching
public class AntaressDataManagerProperties {

    @Value("${antares.datamanager.trajectory.file.path}")
    public String trajectoryFilePath;

    @Value("${antares.datamanager.data.remote.directory}")
    public String dataRemoteDirectory;

    @Value("${antares.datamanager.data.host}")
    public String dataHost;

    @Value("${antares.datamanager.data.host.username}")
    public String dataHostUsername;

    @Value("${antares.datamanager.data.host.password}")
    public String dataHostPassword;

    @Value("${antares.datamanager.data.local.directory.storage}")
    public String dataLocalDirectoryStorage;


    @Value("${antares.datamanager.area.directory}")
    public String areaDirectory;

    @Value("${antares.datamanager.link.directory}")
    public String linkDirectory;

    @Value("${antares.datamanager.thermal.cost.directory}")
    public String thermalCostDirectory;

    @Value("${antares.datamanager.thermal.capacity.directory}")
    public String thermalCapacityDirectory;

    @Value("${antares.datamanager.thermal.parameter.directory}")
    public String thermalParameterDirectory;

}
