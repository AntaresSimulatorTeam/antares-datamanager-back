package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.PriceTakingOrderEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
        assertThat(result).isNull();
    }

    @Test
    void buildAdequacySettingsMap_shouldReturnMappedValues_whenSettingsPresent() {
        AdequacySettingsEntity settings = AdequacySettingsEntity.builder()
                .includeAdqPatch(true)
                .setToNullNtcFromPhysicalOutToPhysicalInForFirstStep(false)
                .priceTakingOrder(PriceTakingOrderEnum.DENS)
                .includeHurdleCostCsr(true)
                .checkCsrCostFunction(false)
                .thresholdInitiateCurtailmentSharingRule(10)
                .thresholdDisplayLocalMatchingRuleViolations(20)
                .thresholdCsrVariableBoundsRelaxation(30)
                .enableFirstStep(true)
                .setToNullNtcBetweenPhysicalOutForFirstStep(false)
                .redispatch(true)
                .build();

        Map<String, Object> result = adequacySettingsToJsonService.buildAdequacySettingsMap(Optional.of(settings));

        assertThat(result).isNotNull().containsKey("adequacy");
        Map<String, Object> adequacy = (Map<String, Object>) result.get("adequacy");
        
        assertThat(adequacy.get("include_adq_patch")).isEqualTo(true);
        assertThat(adequacy.get("set_to_null_ntc_from_physical_out_to_physical_in_for_first_step")).isEqualTo(false);
        assertThat(adequacy.get("price_taking_order")).isEqualTo(0); // DENS -> 0
        assertThat(adequacy.get("include_hurdle_cost_csr")).isEqualTo(true);
        assertThat(adequacy.get("check_csr_cost_function")).isEqualTo(false);
        assertThat(adequacy.get("threshold_initiate_curtailment_sharing_rule")).isEqualTo(10.0);
        assertThat(adequacy.get("threshold_display_local_matching_rule_violations")).isEqualTo(20.0);
        assertThat(adequacy.get("threshold_csr_variable_bounds_relaxation")).isEqualTo(30.0);
        assertThat(adequacy.get("enable_first_step")).isEqualTo(true);
        assertThat(adequacy.get("set_to_null_ntc_between_physical_out_for_first_step")).isEqualTo(false);
        assertThat(adequacy.get("redispatch")).isEqualTo(true);
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
