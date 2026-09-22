package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "hydro_allocation_me", indexes = {
        @Index(name = "idx_hydro_alloc_trajectory", columnList = "trajectory_id"),
        @Index(name = "idx_hydro_alloc_area", columnList = "area")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HydroAllocationMeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trajectory_id", nullable = false)
    private Integer trajectoryId;

    @Column(name = "area", nullable = false, length = 60)
    private String area;

    @Column(name = "node", length = 60)
    private String node;

    @Column(name = "allocation_coefficient")
    private BigDecimal allocationCoefficient;

}
