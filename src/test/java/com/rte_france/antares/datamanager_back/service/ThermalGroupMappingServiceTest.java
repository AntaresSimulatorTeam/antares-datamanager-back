package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.ThermalGroupMappingRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalGroupMappingEntity;
import com.rte_france.antares.datamanager_back.service.impl.ThermalGroupMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThermalGroupMappingServiceTest {

    @Mock
    private ThermalGroupMappingRepository repository;

    @InjectMocks
    private ThermalGroupMappingService service;

    @Test
    void toGroup_found_caseInsensitive_andTrimmed() {
        var entity = ThermalGroupMappingEntity.builder()
                .cluster("conventional old 1")
                .groupName("Gas")
                .build();

        when(repository.findByClusterIgnoreCase("conventional old 1")).thenReturn(Optional.of(entity));
        when(repository.findByClusterIgnoreCase("CONVENTIONAL OLD 1")).thenReturn(Optional.of(entity));

        assertThat(service.toGroup(" CONVENTIONAL OLD 1 ")).isEqualTo("Gas");
        assertThat(service.toGroup("conventional old 1")).isEqualTo("Gas");
    }

    @Test
    void toGroup_notFound_returnsDefault_OTHER1() {
        when(repository.findByClusterIgnoreCase("abcdefg")).thenReturn(Optional.empty());
        assertThat(service.toGroup("abcdefg")).isEqualTo("OTHER1");
    }

    @Test
    void toGroup_blank_returnsDefault_OTHER1() {
        assertThat(service.toGroup("   ")).isEqualTo("OTHER1");
    }

    @Test
    void toGroup_null_throwsNpe() {
        assertThatThrownBy(() -> service.toGroup(null))
                .isInstanceOf(NullPointerException.class);
    }
}
