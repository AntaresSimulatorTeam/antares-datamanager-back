package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "settingsSeedsParameters")
@Table(name = "settings_seeds_parameters")
public class SettingsSeedsParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "settings_seeds_parameters_seq_gen")
    @SequenceGenerator(name = "settings_seeds_parameters_seq_gen", sequenceName = "settings_seeds_parameters_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "seed_tsgen_wind")
    private Integer seedTsgenWind;

    @Column(name = "seed_tsgen_load")
    private Integer seedTsgenLoad;

    @Column(name = "seed_tsgen_hydro")
    private Integer seedTsgenHydro;

    @Column(name = "seed_tsgen_thermal")
    private Integer seedTsgenThermal;

    @Column(name = "seed_tsgen_solar")
    private Integer seedTsgenSolar;

    @Column(name = "seed_tsnumbers")
    private Integer seedTsnumbers;

    @Column(name = "seed_unsupplied_energy_costs")
    private Integer seedUnsuppliedEnergyCosts;

    @Column(name = "seed_spilled_energy_costs")
    private Integer seedSpilledEnergyCosts;

    @Column(name = "seed_thermal_costs")
    private Integer seedThermalCosts;

    @Column(name = "seed_hydro_costs")
    private Integer seedHydroCosts;

    @Column(name = "seed_initial_reservoir_levels")
    private Integer seedInitialReservoirLevels;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}
