package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "MiscLoadFactory")
@Table(name = "misc_load_factory")
public class MiscLoadFactoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "misc_load_factory_seq_gen")
    @SequenceGenerator(name = "misc_load_factory_seq_gen", sequenceName = "misc_load_factory_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "ts_name", unique = true)
    private String tsName;

    private String checksum;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

}

