package com.rte_france.antares.datamanager_back.repository.model.flowbased;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "fb_link_list",
        uniqueConstraints = @UniqueConstraint(
                name = "fb_link_list_trajectory_list_position_uk",
                columnNames = {"trajectory_id", "list_name", "position"}),
        indexes = @Index(name = "fb_link_list_trajectory_list_idx",
                columnList = "trajectory_id, list_name"))
public class FlowbasedLinkListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fb_link_list_seq_gen")
    @SequenceGenerator(name = "fb_link_list_seq_gen",
            sequenceName = "fb_link_list_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "list_name", nullable = false, columnDefinition = "TEXT")
    private String listName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String link;

    @Column(nullable = false)
    private Integer position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;
}
