package com.rte_france.antares.datamanager_back;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Import({AntaresDataManagerConfig.class})
@SpringBootApplication
public class PegaseBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(PegaseBackApplication.class, args);
	}

}

