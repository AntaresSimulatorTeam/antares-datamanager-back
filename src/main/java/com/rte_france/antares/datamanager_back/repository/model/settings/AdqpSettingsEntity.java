package com.rte_france.antares.datamanager_back.repository.model.settings;

import jakarta.persistence.*;

@Entity
@Table(name = "adequacy_patch_settings")
public class AdqpSettingsEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "adequacy_patch_settings_seq_gen")
    @SequenceGenerator(name = "adequacy_patch_settings_seq_gen", sequenceName = "adequacy_patch_settings_sequence", allocationSize = 1)
    private Long id;
    @Column(name = "include_adq_patch")
    private Boolean includeAdqPatch;

    @Column(name = "set_to_null_ntc_from_physical_out_to_physical_in_for_first_step")
    private Boolean setToNullNtcFromPhysicalOutToPhysicalInForFirstStep;

    @Column(name = "price_taking_order")
    @Enumerated(EnumType.STRING)
    private PriceTakingOrderEnum priceTakingOrder;

    @Column(name = "include_hurdle_cost_csr")
    private Boolean includeHurdleCostCsr;

    @Column(name = "check_csr_cost_function")
    private Boolean checkCsrCostFunction;

    @Column(name = "threshold_initiate_curtailment_sharing_rule")
    private Integer thresholdInitiateCurtailmentSharingRule;


    @Column(name = "threshold_display_local_matching_rule_violations")
    private Integer thresholdDisplayLocalMatchingRuleViolations;

    @Column(name = "threshold_csr_variable_bounds_relaxation")
    private Integer thresholdCsrVariableBoundsRelaxation;

    @Column(name = "set_to_null_ntc_between_physical_out_for_first_step")
    private Boolean setToNullNtcBetweenPhysicalOutForFirstStep;
    @Column(name = "redispatch")
    private Boolean redispatch;


}