package com.rte_france.antares.datamanager_back.repository.model.p2g;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "P2GParameters")
@Table(name = "p2g_parameters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class P2GParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "p2g_parameters_seq_gen")
    @SequenceGenerator(name = "p2g_parameters_seq_gen", sequenceName = "p2g_parameters_sequence", allocationSize = 1)
    private Integer id;

    private Integer fc_electrolyseur;
    private Integer facteur_surdimension_enr;
    private Integer part_pv_mix;
}
