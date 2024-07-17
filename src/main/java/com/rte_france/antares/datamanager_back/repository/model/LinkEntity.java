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
@Entity(name = "Link")
@Table(name = "link")
public class LinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "link_seq_gen")
    @SequenceGenerator(name = "link_seq_gen", sequenceName = "link_sequence", allocationSize = 1)
    private Integer id;

    private String name;

    private Double winterHpDirectMw;
    private Double winterHpIndirectMw;
    private Double winterHcDirectMw;
    private Double winterHcIndirectMw;
    private Double summerHpDirectMw;
    private Double summerHpIndirectMw;
    private Double summerHcDirectMw;
    private Double summerHcIndirectMw;
    private Boolean flowbasedPerimeter;
    private Boolean hvdc;
    private Boolean specificTs;
    private Boolean forcedOutageHvac;
    private double hurdleCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

}
