package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.lang.Double;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_specific_parameters")
public class ThermalSpecificParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_specific_parameters_seq_gen")
    @SequenceGenerator(name = "thermal_specific_parameters_seq_gen", sequenceName = "thermal_specific_parameters_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "node", length = 20)
    private String node;

    @Column(name = "node_entsoe", length = 20)
    private String nodeEntsoe;

    @Column(name = "comments", length = 255)
    private String comment;

    @Column(name = "cluster_pemmdb", length = 20)
    private String clusterPemmdb;

    @Column(name = "cluster", length = 20)
    private String cluster;

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

    @Column(name = "marginal_cost")
    private Double marginalCost;

    @Column(name = "market_bid")
    private Double marketBid;

    @Column(name = "mr_specific")
    private Integer mrSpecific;

    @Column(name = "cm_specific")
    private Integer cmSpecific;

    @Column(name = "npo_max_winther")
    private Integer npoMaxWinther;

    @Column(name = "npo_max_summer")
    private Integer npoMaxSummer;

    @Column(name = "nb_unit")
    private Integer nbUnit;

    @Column(name = "po_winter_rate")
    private Double poWinterRate;

    @Column(name = "f1") private Double f1;
    @Column(name = "f2") private Double f2;
    @Column(name = "f3") private Double f3;
    @Column(name = "f4") private Double f4;
    @Column(name = "f5") private Double f5;
    @Column(name = "f6") private Double f6;
    @Column(name = "f7") private Double f7;
    @Column(name = "f8") private Double f8;
    @Column(name = "f9") private Double f9;
    @Column(name = "f10") private Double f10;
    @Column(name = "f11") private Double f11;
    @Column(name = "f12") private Double f12;

    @Column(name = "p1") private Integer p1;
    @Column(name = "p2") private Integer p2;
    @Column(name = "p3") private Integer p3;
    @Column(name = "p4") private Integer p4;
    @Column(name = "p5") private Integer p5;
    @Column(name = "p6") private Integer p6;
    @Column(name = "p7") private Integer p7;
    @Column(name = "p8") private Integer p8;
    @Column(name = "p9") private Integer p9;
    @Column(name = "p10") private Integer p10;
    @Column(name = "p11") private Integer p11;
    @Column(name = "p12") private Integer p12;

}
