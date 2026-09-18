package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity(name = "GroupAreaDesc")
@Table(name = "group_area_desc")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupAreaDescEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "group_area_desc_seq_gen")
    @SequenceGenerator(name = "group_area_desc_seq_gen", sequenceName = "group_area_desc_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "group_name", length = 60, nullable = false)
    private String groupName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @OneToMany(mappedBy = "groupArea", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ListAreaDescEntity> areas;

}
