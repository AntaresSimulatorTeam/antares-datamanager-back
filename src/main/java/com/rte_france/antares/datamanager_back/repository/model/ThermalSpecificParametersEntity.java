package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.lang.Double;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_specific_parameters")
public class ThermalSpecificParametersEntity extends ThermalBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_specific_parameters_seq_gen")
    @SequenceGenerator(name = "thermal_specific_parameters_seq_gen", sequenceName = "thermal_specific_parameters_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "node", length = 20)
    private String node;

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

    @Column(name = "npo_max_winter")
    private Integer npoMaxWinter;

    @Column(name = "npo_max_summer")
    private Integer npoMaxSummer;

    @Column(name = "nb_unit")
    private Integer nbUnit;


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

    @Column(name = "p1") private Double p1;
    @Column(name = "p2") private Double p2;
    @Column(name = "p3") private Double p3;
    @Column(name = "p4") private Double p4;
    @Column(name = "p5") private Double p5;
    @Column(name = "p6") private Double p6;
    @Column(name = "p7") private Double p7;
    @Column(name = "p8") private Double p8;
    @Column(name = "p9") private Double p9;
    @Column(name = "p10") private Double p10;
    @Column(name = "p11") private Double p11;
    @Column(name = "p12") private Double p12;

    @Size(max = 50)
    @Column(name = "area")
    private String area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thermal_cluster_ref_id")
    private ThermalClusterRef thermalClusterRef;

}
