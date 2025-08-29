package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_cluster_ref")
public class ThermalClusterRef {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_cluster_ref_sequence_gen")
    @SequenceGenerator(name = "thermal_cluster_ref_sequence_gen", sequenceName = "thermal_cluster_ref_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Size(max = 40)
    @Column(name = "name", length = 40)
    private String name;

    @Size(max = 40)
    @Column(name = "name_pemmdb", length = 40)
    private String namePemmdb;

    @ManyToOne(fetch = FetchType.LAZY, cascade =  CascadeType.ALL)
    @JoinColumn(name = "thermal_technology_id")
    private ThermalTechnology thermalTechnology;

}