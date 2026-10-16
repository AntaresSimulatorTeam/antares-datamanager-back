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
@Table(name = "fb_constraints_restrictions")
public class FlowbasedConstraintsRestrictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fb_constraints_restrictions_seq_gen")
    @SequenceGenerator(name = "fb_constraints_restrictions_seq_gen",
            sequenceName = "fb_constraints_restrictions_sequence", allocationSize = 1)
    private Integer id;

    @Column(columnDefinition = "TEXT")
    private String name;

    private Boolean enabled;

    @Column(columnDefinition = "TEXT")
    private String sign;

    @Column(columnDefinition = "TEXT")
    private String type;

    @Column(name = "constraint_type", columnDefinition = "TEXT")
    private String constraintType;

    @Column(name = "list_links", columnDefinition = "TEXT")
    private String listLinks;

    @Column(name = "second_member", columnDefinition = "TEXT")
    private String secondMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;
}
