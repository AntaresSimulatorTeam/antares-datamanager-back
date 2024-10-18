package com.rte_france.antares.datamanager_back.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

@AutoConfiguration
public class SwaggerListenerConfiguration implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final String CONTEXT_PATH = "ms.context.path";
    private static final String API_PATH = "ms.api.path";
    private static final String SWAGGER_CONFIG_URL_PREFIX = "/v3/api-docs/swagger-config";
    private static final String SWAGGER_URL_PREFIX = "/v3/api-docs";
    private static final String SWAGGER_UI_CONFIG_URL_PROPERTY = "springdoc.swagger-ui.config-url";
    private static final String SWAGGER_UI_URL_PROPERTY = "springdoc.swagger-ui.url";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        String msContextPath = environment.getProperty(CONTEXT_PATH, String.class);
        String msApiPath = environment.getProperty(API_PATH, String.class);
        String swaggerConfigUrl = msContextPath + msApiPath + SWAGGER_CONFIG_URL_PREFIX;
        String swaggerUrl = msContextPath + msApiPath + SWAGGER_URL_PREFIX;

        Properties props = new Properties();
        props.put(SWAGGER_UI_CONFIG_URL_PROPERTY, swaggerConfigUrl);
        props.put(SWAGGER_UI_URL_PROPERTY, swaggerUrl);
        environment.getPropertySources().addFirst(new PropertiesPropertySource("commonLibProps", props));
    }

}
