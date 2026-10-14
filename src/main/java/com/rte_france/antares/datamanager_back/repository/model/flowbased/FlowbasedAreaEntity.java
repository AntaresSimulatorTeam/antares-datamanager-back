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
@Table(name = "areas_fb")
public class FlowbasedAreaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "areas_fb_seq_gen")
    @SequenceGenerator(name = "areas_fb_seq_gen", sequenceName = "areas_fb_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "area", nullable = false)
    private String area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;
}
