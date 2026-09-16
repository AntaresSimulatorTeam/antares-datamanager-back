package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "ListAreaDesc")
@Table(name = "list_area_desc", uniqueConstraints = {
    @UniqueConstraint(name = "list_area_desc_uk_group_area", columnNames = {"group_area_id", "area"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListAreaDescEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "list_area_desc_seq_gen")
    @SequenceGenerator(name = "list_area_desc_seq_gen", sequenceName = "list_area_desc_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "area", length = 20, nullable = false)
    private String area;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_area_id", nullable = false)
    private GroupAreaDescEntity groupArea;

}
