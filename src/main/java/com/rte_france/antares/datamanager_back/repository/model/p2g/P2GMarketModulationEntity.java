package com.rte_france.antares.datamanager_back.repository.model.p2g;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "P2GMarketModulation")
@Table(name = "p2g_market_modulation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class P2GMarketModulationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "p2g_market_modulation_seq_gen")
    @SequenceGenerator(name = "p2g_market_modulation_seq_gen", sequenceName = "p2g_market_modulation_sequence", allocationSize = 1)
    private Integer id;
    
    private String name;
}
