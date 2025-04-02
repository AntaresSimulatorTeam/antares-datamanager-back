package com.rte_france.antares.datamanager_back.configuration.logback;

import org.apache.catalina.startup.Tomcat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(Tomcat.class)
@ConditionalOnWebApplication
public class LogbackAccessConfig {
    @Bean
    public AntaresLogbackAccessConfigurator createLogBackAccessConfigurator() {
        return  new AntaresLogbackAccessConfigurator();
    }
}
