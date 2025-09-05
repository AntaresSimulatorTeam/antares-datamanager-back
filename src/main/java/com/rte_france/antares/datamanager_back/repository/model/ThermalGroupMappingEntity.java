package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "thermal_group_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ThermalGroupMappingEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_group_mapping_seq_gen")
  @SequenceGenerator(name = "thermal_group_mapping_seq_gen", sequenceName = "thermal_group_mapping_sequence", allocationSize = 1)
  @Column(name = "id", nullable = false)
  private Integer id;

  @Column(name = "cluster", nullable = false, unique = true)
  private String cluster;

  @Column(name = "group_name", nullable = false)
  private String groupName;
}
