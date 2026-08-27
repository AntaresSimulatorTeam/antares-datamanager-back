package com.rte_france.antares.datamanager_back.repository.model.p2g;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "P2GCapacity")
@Table(name = "p2g_capacity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class P2GCapacityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "p2g_capacity_seq_gen")
    @SequenceGenerator(name = "p2g_capacity_seq_gen", sequenceName = "p2g_capacity_sequence", allocationSize = 1)
    private Integer id;

    private String area;
    private Integer base_fatal_band;
    private Integer base_eff;
    private Integer base_capacity;
    private Integer marg_capacity;
    private Integer methanation_capacity;
    private Integer asservi_capacity;
}
