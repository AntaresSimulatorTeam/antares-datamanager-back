package com.rte_france.antares.datamanager_back.configuration.logback;

import ch.qos.logback.access.tomcat.LogbackValve;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

@Slf4j
public class AntaresLogbackAccessConfigurator implements WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> {

    private static final  String LOGBACK_ACCESS_FILE_NAME = "logback-access.xml";
    private static final String SERVICE_NAME_CUSTOM_FIELD = "application";

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("#{'${spring.application.name:${project.title:pegase-back}}'}")
    private String applicationName;


    @Override
    public void customize(ConfigurableTomcatWebServerFactory factory) {
        final LogbackValve logbackValve = new LogbackValve();
        try {
            resourceLoader.getResource(LOGBACK_ACCESS_FILE_NAME).getURI();
            logbackValve.setFilename(LOGBACK_ACCESS_FILE_NAME);
            logbackValve.getPropertyMap().put(SERVICE_NAME_CUSTOM_FIELD, applicationName);
            factory.addEngineValves(logbackValve);
        } catch (IOException e) {
            log.warn(String.format("error while reading %s file : %s ", LOGBACK_ACCESS_FILE_NAME, e.getMessage()));
        }
    }

}
