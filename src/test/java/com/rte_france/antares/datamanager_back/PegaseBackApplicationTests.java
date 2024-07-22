package com.rte_france.antares.datamanager_back;

import com.rte_france.antares.datamanager_back.controller.TrajectoryController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PegaseBackApplicationTests {

	@Autowired
	private TrajectoryController trajectoryController;

	@Test
	void contextLoads() {
		Assertions.assertNotNull(trajectoryController);
	}

}
