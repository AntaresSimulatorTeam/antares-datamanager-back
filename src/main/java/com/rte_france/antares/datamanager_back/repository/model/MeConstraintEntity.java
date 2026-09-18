package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "MeConstraint")
@Table(name = "me_constraint")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeConstraintEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "me_constraint_seq_gen")
    @SequenceGenerator(name = "me_constraint_seq_gen", sequenceName = "me_constraint_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "name", length = 40)
    private String name;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "sign", length = 20)
    private String sign;

    @Column(name = "temporality", length = 20)
    private String temporality;

    @Column(name = "type", length = 5)
    private String type;

    @Column(name = "comments", length = 200)
    private String comments;

    @Column(name = "noeud1_gauche", length = 60)
    private String noeud1Gauche;

    @Column(name = "noeud2_gauche", length = 60)
    private String noeud2Gauche;

    @Column(name = "cluster_gauche", length = 60)
    private String clusterGauche;

    @Column(name = "noeud1_droite", length = 60)
    private String noeud1Droite;

    @Column(name = "noeud2_droite", length = 60)
    private String noeud2Droite;

    @Column(name = "cluster_droite", length = 60)
    private String clusterDroite;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

}
