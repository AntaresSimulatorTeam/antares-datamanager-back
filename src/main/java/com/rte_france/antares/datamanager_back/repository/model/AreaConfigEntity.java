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
@Entity(name = "AreaConfigEntity")
@Table(name = "area_config")
public class AreaConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "area_config_seq_gen")
    @SequenceGenerator(name = "area_config_seq_gen", sequenceName = "area_config_sequence", allocationSize = 1)
    private Integer id;

    private String district;

    @Column(name = "spilled_energy_cost", nullable = false)
    private Double spilledEnergyCost;

    @Column(name = "unsupplied_energy_cost", nullable = false)
    private Double unsuppliedEnergyCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private AreaEntity area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;


    public AreaConfigEntity(String district, Double spilledEnergyCost, Double unsuppliedEnergyCost, AreaEntity areaEntity) {
        this.district = district;
        this.spilledEnergyCost = spilledEnergyCost;
        this.unsuppliedEnergyCost = unsuppliedEnergyCost;
        this.area = areaEntity;
    }


}
