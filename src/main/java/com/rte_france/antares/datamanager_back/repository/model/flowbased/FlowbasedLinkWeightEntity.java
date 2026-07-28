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
@Table(name = "fb_links_weight")
public class FlowbasedLinkWeightEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fb_links_weight_seq_gen")
    @SequenceGenerator(name = "fb_links_weight_seq_gen", sequenceName = "fb_type_day_sequence", allocationSize = 1)
    private Integer id;
    
    private String weight;
    
    private String link;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}