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

    @Column(name = "hydro_heuristic_policy")
    private String hydroHeuristicPolicy;

    @Column(name = "hydro_pricing_mode")
    private String hydroPricingMode;

    @Column(name = "power_fluctuations")
    private String powerFluctuations;

    @Column(name = "shedding_policy")
    private String sheddingPolicy;

    @Column(name = "unit_commitment_mode")
    private String unitCommitmentMode;

    @Column(name = "number_of_cores_mode")
    private String numberOfCoresMode;

    @Column(name = "renewable_generation_modelling")
    private String renewableGenerationModelling;

    @Column(name = "accurate_shave_peaks_include_short_term_storage")
    private Boolean accurateShavePeaksIncludeShortTermtorage;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}
