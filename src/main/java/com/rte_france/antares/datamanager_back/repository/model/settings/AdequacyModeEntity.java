package com.rte_france.antares.datamanager_back.repository.model.settings;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "adequacy_patch_mode")
public class AdequacyModeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "adqp_mode_seq_gen")
    @SequenceGenerator(name = "adqp_mode_seq_gen", sequenceName = "adequacy_patch_mode_sequence", allocationSize = 1)
    private Integer id;

    @Column(nullable = false)
    private String area;

    private String mode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}
