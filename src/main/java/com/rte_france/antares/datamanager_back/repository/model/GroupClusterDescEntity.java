package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity(name = "GroupClusterDesc")
@Table(name = "group_cluster_desc")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupClusterDescEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "group_cluster_desc_seq_gen")
    @SequenceGenerator(name = "group_cluster_desc_seq_gen", sequenceName = "group_cluster_desc_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "group_name", length = 60, nullable = false)
    private String groupName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @OneToMany(mappedBy = "groupCluster", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ListClusterDescEntity> clusters;

}
