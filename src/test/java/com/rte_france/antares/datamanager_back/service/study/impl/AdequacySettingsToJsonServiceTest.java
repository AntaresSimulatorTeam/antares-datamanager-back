package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdequacySettingsToJsonServiceTest {

    private AdequacySettingsToJsonService adequacySettingsToJsonService;

    @BeforeEach
    void setUp() {
        adequacySettingsToJsonService = new AdequacySettingsToJsonService();
    }

    @Test
    void buildAdequacySettingsMap_shouldReturnNull_whenSettingsNotPresent() {
        Map<String, Object> result = adequacySettingsToJsonService.buildAdequacySettingsMap(Optional.empty());
        assertThat(result).isEmpty();
    }

    @Test
    void buildAdequacySettingsMap_shouldReturnMappedValues_whenSettingsPresent() {
        AdequacySettingsEntity settings = AdequacySettingsEntity.builder()
                .includeAdqPatch(true)
                .priceTakingOrder("DENS")
                .includeHurdleCostCsr(true)
                .checkCsrCostFunction(false)
                .thresholdInitiateCurtailmentSharingRule(10)
                .thresholdDisplayLocalMatchingRuleViolations(20)
                .thresholdCsrVariableBoundsRelaxation(30)
                .redispatch(true)
                .setToNullNtcFromPhysicalOutToPhysicalInForFirstStep(true)
                .build();

        Map<String, Object> result = adequacySettingsToJsonService.buildAdequacySettingsMap(Optional.of(settings));

        assertThat(result).isNotNull().containsKey("adequacy");
        Map<String, Object> adequacy = (Map<String, Object>) result.get("adequacy");

        assertThat(adequacy)
                    .containsEntry("include_adq_patch", true)
                    .containsEntry("price_taking_order", "DENS")
                    .containsEntry("include_hurdle_cost_csr", true)
                    .containsEntry("check_csr_cost_function", false)
                    .containsEntry("threshold_initiate_curtailment_sharing_rule", 10.0)
                    .containsEntry("threshold_display_local_matching_rule_violations", 20.0)
                    .containsEntry("threshold_csr_variable_bounds_relaxation", 30.0)
                    .containsEntry("redispatch", true)
                    .containsEntry("set_to_null_ntc_from_physical_out_to_physical_in_for_first_step", true);

        assertThat(adequacy).doesNotContainKey("ntc_between_physical_areas_out_adequacy_patch");
    }

    @Test
    void buildAdequacySettingsMap_shouldOmitNullFields() {
        AdequacySettingsEntity settings = new AdequacySettingsEntity(); // All null

        Map<String, Object> result = adequacySettingsToJsonService.buildAdequacySettingsMap(Optional.of(settings));

        assertThat(result).isNotNull().containsKey("adequacy");
        Map<String, Object> adequacy = (Map<String, Object>) result.get("adequacy");
        assertThat(adequacy).isEmpty();
    }
}
