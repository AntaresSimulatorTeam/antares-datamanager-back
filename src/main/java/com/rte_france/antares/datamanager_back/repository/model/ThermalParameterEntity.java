package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;


@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_parameter")
public class ThermalParameterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_parameter_seq_gen")
    @SequenceGenerator(name = "thermal_parameter_seq_gen", sequenceName = "thermal_parameter_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "year_parameter")
    private Double year;

    @Size(max = 255)
    @Column(name = "node")
    private String node;

    @Column(name = "node_entsoe")
    private String nodeEntsoe;

    @Column(name = "comments")
    private String comments;

    @Column(name = "category")
    private String category;

    @Column(name = "fuel")
    private String fuel;

    @Column(name = "techno")
    private String techno;

    @Column(name = "cluster_pemmdb")
    private String clusterPemmdb;

    @Column(name = "cluster")
    private String cluster;

    @Column(name = "enabled")
    private Boolean enabled;

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

    @Column(name = "min_stable_generation")
    private Double minStableGeneration;

    @Column(name = "spinning")
    private Double spinning;

    @Column(name = "efficiency")
    private Double efficiency;

    @Column(name = "fo_rate")
    private Double foRate;

    @Column(name = "fo_duration")
    private Double foDuration;

    @Column(name = "po_duration")
    private Double poDuration;

    @Column(name = "po_winter")
    private Double poWinter;

    @Column(name = "f1")
    private Double f1;

    @Column(name = "f2")
    private Double f2;

    @Column(name = "f3")
    private Double f3;

    @Column(name = "f4")
    private Double f4;

    @Column(name = "f5")
    private Double f5;

    @Column(name = "f6")
    private Double f6;

    @Column(name = "f7")
    private Double f7;

    @Column(name = "f8")
    private Double f8;

    @Column(name = "f9")
    private Double f9;

    @Column(name = "f10")
    private Double f10;

    @Column(name = "f11")
    private Double f11;

    @Column(name = "f12")
    private Double f12;

    @Column(name = "p1")
    private Double p1;

    @Column(name = "p2")
    private Double p2;

    @Column(name = "p3")
    private Double p3;

    @Column(name = "p4")
    private Double p4;

    @Column(name = "p5")
    private Double p5;

    @Column(name = "p6")
    private Double p6;

    @Column(name = "p7")
    private Double p7;

    @Column(name = "p8")
    private Double p8;

    @Column(name = "p9")
    private Double p9;

    @Column(name = "p10")
    private Double p10;

    @Column(name = "p11")
    private Double p11;

    @Column(name = "p12")
    private Double p12;

    @Column(name = "spread")
    private Double spread;

    @Column(name = "marginal_cost")
    private Double marginalCost;

    @Column(name = "market_bid")
    private Double marketBid;

    @Column(name = "fixed_cost")
    private Double fixedCost;

    @Column(name = "offset_variable_cost")
    private Double offsetVariableCost;

    @Column(name = "npo_max_winter")
    private Double npoMaxWinter;

    @Column(name = "npo_max_summer")
    private Double npoMaxSummer;

    @Column(name = "nb_units")
    private Double nbUnits;

    @Column(name = "mr_specific")
    private Double mrSpecific;

    @Column(name = "m1")
    private Double m1;

    @Column(name = "m2")
    private Double m2;

    @Column(name = "m3")
    private Double m3;

    @Column(name = "m4")
    private Double m4;

    @Column(name = "m5")
    private Double m5;

    @Column(name = "m6")
    private Double m6;

    @Column(name = "m7")
    private Double m7;

    @Column(name = "m8")
    private Double m8;

    @Column(name = "m9")
    private Double m9;

    @Column(name = "m10")
    private Double m10;

    @Column(name = "m11")
    private Double m11;

    @Column(name = "m12")
    private Double m12;

    @Column(name = "cm_specific")
    private Double cmSpecific;

    @Column(name = "c1")
    private Double c1;

    @Column(name = "c2")
    private Double c2;

    @Column(name = "c3")
    private Double c3;

    @Column(name = "c4")
    private Double c4;

    @Column(name = "c5")
    private Double c5;

    @Column(name = "c6")
    private Double c6;

    @Column(name = "c7")
    private Double c7;

    @Column(name = "c8")
    private Double c8;

    @Column(name = "c9")
    private Double c9;

    @Column(name = "c10")
    private Double c10;

    @Column(name = "c11")
    private Double c11;

    @Column(name = "c12")
    private Double c12;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

}