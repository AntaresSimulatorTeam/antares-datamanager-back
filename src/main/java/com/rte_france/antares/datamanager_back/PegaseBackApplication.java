package com.rte_france.antares.datamanager_back;

import com.rte_france.antares.datamanager_back.configuration.security.CorsConfig;
import com.rte_france.antares.datamanager_back.configuration.OpenApiConfig;
import com.rte_france.antares.datamanager_back.configuration.security.SecurityConfig;
import com.rte_france.antares.datamanager_back.exception.AntaresExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import({SecurityConfig.class, OpenApiConfig.class, CorsConfig.class, AntaresExceptionHandler.class})
@SpringBootApplication
public class PegaseBackApplication {
	public static void main(String[] args) {
		SpringApplication.run(PegaseBackApplication.class, args);
	}
}

