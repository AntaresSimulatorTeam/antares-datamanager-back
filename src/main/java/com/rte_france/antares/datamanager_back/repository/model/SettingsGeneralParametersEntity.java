package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "SettingsGeneralParameters")
@Table(name = "settings_general_parameters")
public class SettingsGeneralParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "settings_general_parameters_seq_gen")
    @SequenceGenerator(name = "settings_general_parameters_seq_gen", sequenceName = "settings_general_parameters_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "mode")
    private String mode;

    @Column(name = "first_day")
    private Integer firstDay;

    @Column(name = "last_day")
    private Integer lastDay;

    @Column(name = "horizon")
    private String horizon;

    @Column(name = "first_month")
    private String firstMonth;

    @Column(name = "first_week_day")
    private String firstWeekDay;

    @Column(name = "first_january")
    private String firstJanuary;

    @Column(name = "leap_year")
    private Boolean leapYear;

    @Column(name = "nb_years")
    private Integer nbYears;

    @Column(name = "building_mode")
    private String buildingMode;

    @Column(name = "selection_mode")
    private Boolean selectionMode;

    @Column(name = "year_by_year")
    private Boolean yearByYear;

    @Column(name = "simulation_synthesis")
    private Boolean simulationSynthesis;

    @Column(name = "mc_scenario")
    private Boolean mcScenario;

    @Column(name = "thematic_trimming")
    private Boolean thematicTrimming;

    @Column(name = "geographic_trimming")
    private Boolean geographicTrimming;

    @Column(name = "nb_timeseries_thermal")
    private Integer nbTimeseriesThermal;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}
