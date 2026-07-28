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
@Table(name = "fb_type_day")
public class FlowbasedTypeDayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fb_type_day_seq_gen")
    @SequenceGenerator(name = "fb_type_day_seq_gen", sequenceName = "fb_type_day_sequence", allocationSize = 1)
    private Integer id;
    
    private String clustering;

    @Column(name = "id_type_day", nullable = false)
    private Integer idTypeDay;

    @Column(name = "class_day")
    private String classDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}