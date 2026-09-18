package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "ListClusterDesc")
@Table(name = "list_cluster_desc", uniqueConstraints = {
    @UniqueConstraint(name = "list_cluster_desc_uk_group_cluster", columnNames = {"group_cluster_id", "cluster"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListClusterDescEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "list_cluster_desc_seq_gen")
    @SequenceGenerator(name = "list_cluster_desc_seq_gen", sequenceName = "list_cluster_desc_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "cluster", length = 40, nullable = false)
    private String cluster;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_cluster_id", nullable = false)
    private GroupClusterDescEntity groupCluster;

}
