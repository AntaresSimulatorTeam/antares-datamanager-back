package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.List;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_cost_type")
public class ThermalCostTypeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_cost_type_seq_gen")
    @SequenceGenerator(name = "thermal_cost_type_seq_gen", sequenceName = "thermal_cost_type_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "country")
    private String country;

    @Column(name = "fuel")
    private String fuel;

    @Column(name = "scenario")
    private String scenario;

    @Column(name = "comment")
    private String comment;

    @Column(name = "unit")
    private String unit;

    @Column(name = "modulation")
    private String modulation;

    @Column(name = "ratio_ncv_hcv")
    private Double ratioNcvHcv;


    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "thermalType", cascade = {CascadeType.ALL})
    List<ThermalCostEntity> thermalCostEntities;

}