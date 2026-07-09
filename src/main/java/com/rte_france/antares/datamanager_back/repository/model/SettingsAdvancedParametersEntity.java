package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "SettingsAdvancedParameters")
@Table(name = "settings_advanced_parameters")
public class SettingsAdvancedParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "settings_advanced_parameters_seq_gen")
    @SequenceGenerator(name = "settings_advanced_parameters_seq_gen", sequenceName = "settings_advanced_parameters_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "accuracy_on_correlation")
    private String accuracyOnCorrelation;

    @Column(name = "initial_reservoir_levels")
    private String initialReservoirLevels;

    @Column(name = "power_fluctuations")
    private String powerFluctuations;

    @Column(name = "shedding_policy")
    private String sheddingPolicy;

    @Column(name = "hydro_pricing_mode")
    private String hydroPricingMode;

    @Column(name = "hydro_heuristic_policy")
    private String hydroHeuristicPolicy;

    @Column(name = "unit_commitment_mode")
    private String unitCommitmentMode;

    @Column(name = "number_of_cores_mode")
    private String numberOfCoresMode;

    @Column(name = "day_ahead_reserve_management")
    private String dayAheadReserveManagement;

    @Column(name = "renewable_generation_modelling")
    private String renewableGenerationModelling;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}
