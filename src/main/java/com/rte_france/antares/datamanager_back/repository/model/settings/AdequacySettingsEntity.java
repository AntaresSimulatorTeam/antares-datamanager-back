package com.rte_france.antares.datamanager_back.repository.model.settings;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "adequacy_patch_settings")
public class AdequacySettingsEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "adequacy_patch_settings_seq_gen")
    @SequenceGenerator(name = "adequacy_patch_settings_seq_gen", sequenceName = "adequacy_patch_settings_sequence", allocationSize = 1)
    private Integer id;
    @Column(name = "include_adq_patch")
    private Boolean includeAdqPatch;

    @Column(name = "price_taking_order")
    private String priceTakingOrder;

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

    @Column(name = "redispatch")
    private Boolean redispatch;

    @Column(name = "set_to_null_ntc_from_physical_out_to_physical_in_for_first_step")
    private Boolean setToNullNtcFromPhysicalOutToPhysicalInForFirstStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

}