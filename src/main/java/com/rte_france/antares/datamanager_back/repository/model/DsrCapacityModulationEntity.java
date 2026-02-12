package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "DsrCapacityModulation")
@Table(name = "dsr_capacity_modulation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DsrCapacityModulationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dsr_capacity_modulation_seq_gen")
    @SequenceGenerator(name = "dsr_capacity_modulation_seq_gen", sequenceName = "dsr_capacity_modulation_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @Column(name = "ts_name", length = 40)
    private String tsName;

    @Column(name = "checksum", length = 255)
    private String checksum;
}
