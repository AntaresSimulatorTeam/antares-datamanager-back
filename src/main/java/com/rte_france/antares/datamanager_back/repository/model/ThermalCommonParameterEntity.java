package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;


@EqualsAndHashCode(callSuper = true)
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_common_parameters")
public class ThermalCommonParameterEntity extends ThermalBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_parameter_seq_gen")
    @SequenceGenerator(name = "thermal_parameter_seq_gen", sequenceName = "thermal_parameter_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "year_parameter")
    private Double year;

    @Size(max = 255)
    @Column(name = "cluster")
    private String cluster;

    @Column(name = "cluster_pemmdb")
    private String clusterPemmdb;

    @Column(name = "category")
    private Double category;

    @Column(name = "fuel")
    private String fuel;

    @Column(name = "type")
    private String type;

    @Column(name = "efficiency_range")
    private String efficiencyRange;

    @Column(name = "efficiency_default")
    private Double efficiencyDefault;

    @Column(name = "co2")
    private Double co2;

    @Column(name = "om_cost")
    private Double omCost;

    @Column(name = "min_up_time")
    private Double minUpTime;

    @Column(name = "min_down_time")
    private Double minDownTime;

    @Column(name = "start_up_fuel")
    private Double startUpFuel;

    @Column(name = "start_up_fix_cost")
    private Double startUpFixCost;

    @Column(name = "start_up_fuel_cold_start")
    private Double startUpFuelColdStart;

    @Column(name = "start_up_fix_cost_cold_start")
    private Double startUpFixCostColdStart;

    @Column(name = "start_up_fuel_hot_start")
    private Double startUpFuelHotStart;

    @Column(name = "start_up_fix_cost_hot_start")
    private Double startUpFixCostHotStart;

    @Column(name = "transition_hot_warm")
    private Double transitionHotWarm;

    @Column(name = "transition_hot_cold")
    private Double transitionHotCold;

    @Column(name = "shutdown_time")
    private Double shutdownTime;

    @Column(name = "startup_time")
    private Double startupTime;

    @Column(name = "fo_rate_default")
    private Double foRateDefault;

    @Column(name = "fo_duration_default")
    private Double foDurationDefault;

    @Column(name = "po_duration_default")
    private Double poDurationDefault;

    @Column(name = "po_winter_default")
    private Double poWinterDefault;

    @Column(name = "min_stable_generation_default")
    private Double minStableGenerationDefault;

    @Column(name = "ramp_up")
    private Double rampUp;

    @Column(name = "ramp_down")
    private Double rampDown;

    @Column(name = "fixed_generation_reduction")
    private Double fixedGenerationReduction;
}