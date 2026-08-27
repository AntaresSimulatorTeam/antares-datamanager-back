package com.rte_france.antares.datamanager_back.repository.model.p2g;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "P2GCost")
@Table(name = "p2g_costs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class P2GCostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "p2g_costs_seq_gen")
    @SequenceGenerator(name = "p2g_costs_seq_gen", sequenceName = "p2g_costs_sequence", allocationSize = 1)
    private Integer id;

    private P2GTypeEnum type;
    private String modulation;
    private Integer cost;
}
