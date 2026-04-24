package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "HydroAllocation")
@Table(name = "hydro_allocation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HydroAllocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hydro_allocation_seq_gen")
    @SequenceGenerator(name = "hydro_allocation_seq_gen", sequenceName = "hydro_allocation_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @Column(name = "hydro", length = 20)
    private String hydro;

    @Column(name = "load", length = 20)
    private String load;

    @Column(name = "allocation")
    private Integer allocation;
}
