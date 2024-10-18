package com.rte_france.antares.datamanager_back;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerConfig;
import com.rte_france.antares.datamanager_back.configuration.OpenApiConfiguration;
import com.rte_france.antares.datamanager_back.security.config.rest.OauthSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
@Import({OauthSecurityConfig.class, OpenApiConfiguration.class,
		AntaresDataManagerConfig.class})
@SpringBootApplication
public class PegaseBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(PegaseBackApplication.class, args);
	}

}

