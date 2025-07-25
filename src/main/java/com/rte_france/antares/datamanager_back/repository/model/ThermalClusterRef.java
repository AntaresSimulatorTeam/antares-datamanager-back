package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_cluster_ref", schema = "pegase_local_db_schema")
public class ThermalClusterRef {
    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Size(max = 40)
    @Column(name = "name", length = 40)
    private String name;

    @Size(max = 40)
    @Column(name = "name_pemmdb", length = 40)
    private String namePemmdb;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thermal_technology_id")
    private ThermalTechnology thermalTechnology;

}