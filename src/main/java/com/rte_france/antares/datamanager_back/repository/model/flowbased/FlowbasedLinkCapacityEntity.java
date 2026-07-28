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
@Table(name = "fb_link_capacity")
public class FlowbasedLinkCapacityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fb_link_capacity_seq_gen")
    @SequenceGenerator(name = "fb_link_capacity_seq_gen", sequenceName = "fb_link_capacity_sequence", allocationSize = 1)
    private Integer id;
    
    private String name;

    @Column(name = "winter_HP_direct_MW")
    private Integer winterHPDirectMW;

    @Column(name = "winter_HP_indirect_MW")
    private Integer winterHPIndirectMW;

    @Column(name = "winter_HC_direct_MW")
    private Integer winterHCDirectMW;

    @Column(name = "winter_HC_indirect_MW")
    private Integer winterHCIndirectMW;

    @Column(name = "summer_HP_direct_MW")
    private Integer summerHPDirectMW;

    @Column(name = "summer_HP_indirect_MW")
    private Integer summerHPIndirectMW;

    @Column(name = "summer_HC_direct_MW")
    private Integer summerHCDirectMW;

    @Column(name = "summer_HC_indirect_MW")
    private Integer summerHCIndirectMW;
    
    @Column(name = "hurdles_cost", nullable = false)
    private Boolean hurdlesCost;                       

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}