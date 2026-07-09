package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "settingsOptimizationParameters")
@Table(name = "settings_optimization_parameters")
public class SettingsOptimizationParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "settings_optimization_parameters_seq_gen")
    @SequenceGenerator(name = "settings_optimization_parameters_seq_gen", sequenceName = "settings_optimization_parameters_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "binding_constraints")
    private Boolean bindingConstraints;

    @Column(name = "hurdle_costs")
    private Boolean hurdleCosts;

    @Column(name = "transmission_capacities")
    private String transmissionCapacities;

    @Column(name = "thermal_clusters_min_stable_power")
    private Boolean thermalClustersMinStablePower;

    @Column(name = "thermal_clusters_min_ud_time")
    private Boolean thermalClustersMinUdTime;

    @Column(name = "day_ahead_reserve")
    private Boolean dayAheadReserve;

    @Column(name = "primary_reserve")
    private Boolean primaryReserve;

    @Column(name = "strategic_reserve")
    private Boolean strategicReserve;

    @Column(name = "spinning_reserve")
    private Boolean spinningReserve;

    @Column(name = "export_mps")
    private String exportMps;

    @Column(name = "unfeasible_problem_behavior")
    private String unfeasibleProblemBehavior;

    @Column(name = "simplex_optimization_range")
    private String simplexOptimizationRange;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}
