package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AdequacySettingsToJsonService {

    public Map<String, Object> buildAdequacySettingsMap(Optional<AdequacySettingsEntity> settingsOptional) {
        Map<String, Object> settingsMap = new LinkedHashMap<>();
        if (settingsOptional.isPresent()) {
            AdequacySettingsEntity settings = settingsOptional.get();
            Map<String, Object> adqMap = new LinkedHashMap<>();
            addIfNotNull(adqMap, "include_adq_patch", settings.getIncludeAdqPatch());
            addIfNotNull(adqMap, "price_taking_order", settings.getPriceTakingOrder());
            addIfNotNull(adqMap, "include_hurdle_cost_csr", settings.getIncludeHurdleCostCsr());
            addIfNotNull(adqMap, "check_csr_cost_function", settings.getCheckCsrCostFunction());
            if (settings.getThresholdInitiateCurtailmentSharingRule() != null) {
                adqMap.put("threshold_initiate_curtailment_sharing_rule", settings.getThresholdInitiateCurtailmentSharingRule().doubleValue());
            }
            if (settings.getThresholdDisplayLocalMatchingRuleViolations() != null) {
                adqMap.put("threshold_display_local_matching_rule_violations", settings.getThresholdDisplayLocalMatchingRuleViolations().doubleValue());
            }
            if (settings.getThresholdCsrVariableBoundsRelaxation() != null) {
                adqMap.put("threshold_csr_variable_bounds_relaxation", settings.getThresholdCsrVariableBoundsRelaxation().doubleValue());
            }
            addIfNotNull(adqMap, "redispatch", settings.getRedispatch());
            addIfNotNull(adqMap, "set_to_null_ntc_from_physical_areas_out_to_physical_areas_in_adequacy_patch", settings.getSetToNullNtcFromPhysicalAreasOutToPhysicalAreasInAdequacyPatch());

            settingsMap.put("adequacy", adqMap);
        } else {
            return null;
        }
        return settingsMap;
    }

    private void addIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
