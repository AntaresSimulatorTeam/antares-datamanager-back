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

    @Column(name = "simplex_range")
    private String simplexRange;

    @Column(name = "transmission_capacities")
    private String transmissionCapacities;

    @Column(name = "include_constraints")
    private Boolean includeConstraints;

    @Column(name = "include_hurdlecosts")
    private Boolean includeHurdlecosts;

    @Column(name = "include_tc_minstablepower")
    private Boolean includeTcMinstablepower;

    @Column(name = "include_tc_min_ud_time")
    private Boolean includeTcMinUdTime;

    @Column(name = "include_dayahead")
    private Boolean includeDayahead;

    @Column(name = "include_strategicreserve")
    private Boolean includeStrategicreserve;

    @Column(name = "include_spinningreserve")
    private Boolean includeSpinningreserve;

    @Column(name = "include_primaryreserve")
    private Boolean includePrimaryreserve;

    @Column(name = "include_exportmps")
    private String includeExportmps;

    @Column(name = "include_unfeasible_problem_behavior")
    private String includeUnfeasibleProblemBehavior;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}
