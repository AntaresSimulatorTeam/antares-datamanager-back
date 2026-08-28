package com.rte_france.antares.datamanager_back.repository.model.p2g;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
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

    @Column(name = "fc_electrolyseur")
    private Double fcElectrolyseur;

    @Column(name = "facteur_surdimension_enr")
    private Double facteurSurdimensionEnr;

    @Column(name = "part_pv_mix")
    private Double partPvMix;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}
