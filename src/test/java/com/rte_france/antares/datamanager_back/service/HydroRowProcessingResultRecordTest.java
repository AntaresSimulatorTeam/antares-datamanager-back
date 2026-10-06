package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersEntity;
import com.rte_france.antares.datamanager_back.service.hydro.HydroAllocationRowProcessingResult;
import com.rte_france.antares.datamanager_back.service.hydro.HydroParametersRowProcessingResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class HydroRowProcessingResultRecordTest {
    @Test
    void getEntitiesAllocationsRow_returnEntitiesList(){
        var entities = new ArrayList<HydroAllocationEntity>();
        var result = new HydroAllocationRowProcessingResult(entities, new StringBuilder(), new ArrayList<>());

        var actualEntities = result.getEntities();

        assertThat(actualEntities).isSameAs(entities);
    }

    @Test
    void getEntitiesParametersRow_returnEntitiesList(){
        var entities = new ArrayList<HydroParametersEntity>();
        var result = new HydroParametersRowProcessingResult(entities, new StringBuilder(), new ArrayList<>());

        var actualEntities = result.getEntities();

        assertThat(actualEntities).isSameAs(entities);
    }
}
