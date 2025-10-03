package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "thermal_cost")
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ThermalCostEntity extends ThermalBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_cost_seq_gen")
    @SequenceGenerator(name = "thermal_cost_seq_gen", sequenceName = "thermal_cost_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "cost")
    private Double cost;

    @Column(name = "cost_year")
    private Double year;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thermal_type_id")
    private ThermalCostTypeEntity thermalType;

}