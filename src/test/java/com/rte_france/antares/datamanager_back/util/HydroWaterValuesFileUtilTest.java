package com.rte_france.antares.datamanager_back.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class HydroWaterValuesFileUtilTest {

    @ParameterizedTest
    @CsvSource({
            "waterValues_node1.xlsx, node1, true",
            "NODE1_WATER_VALUES.XLSX, node1, true",
            "waterValues_NODE1.xlsx, node1, true",
            "waterValues_node10.xlsx, node1, false",
            "node10_water_values.xlsx, node1, false",
            "waterValues_node10.xlsx, node10, true",
            "waterValues_node1.xlsx, AT, false",
            "node1_water_values.xlsx, AT, false",
            "waterValues_AT.xlsx, AT, true",
            "unrelated_node1.xlsx, node1, false"
    })
    void matchesNode_shouldMatchOnlyCompleteNodeName(String file, String node, boolean expected) {
        assertThat(HydroWaterValuesFileUtil.matchesNode(file, node)).isEqualTo(expected);
    }
}
