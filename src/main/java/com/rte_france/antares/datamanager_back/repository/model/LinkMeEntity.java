package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "LinkMe")
@Table(name = "link_me")
public class LinkMeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "link_me_seq_gen")
    @SequenceGenerator(name = "link_me_seq_gen", sequenceName = "link_me_sequence", allocationSize = 1)
    private Integer id;

    @Column(nullable = false, length = 60)
    private String nodeFrom;

    @Column(nullable = false, length = 60)
    private String nodeTo;

    private Double directMw;
    private Double indirectMw;
    private Double hurdleCostsDirect;
    private Double hurdleCostsIndirect;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

}
