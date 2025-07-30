package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;


@EqualsAndHashCode(callSuper = true)
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_cluster_capacity")
public final class ThermalClusterCapacityEntity extends ThermalBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_cluster_capacity_seq_gen")
    @SequenceGenerator(name = "thermal_cluster_capacity_seq_gen", sequenceName = "thermal_cluster_capacity_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "to_use")
    private Boolean toUse;

    @Size(max = 50)
    @Column(name = "area")
    private String area;

    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private ThermalCategoryEnum category;

    @Size(max = 10)
    @Column(name = "month_year")
    private String monthYear;

    @Column(name = "capacity")
    private Double value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thermal_cluster_ref_id")
    private ThermalClusterRef thermalClusterRef;

}