package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.ThermalGroupMappingRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalGroupMappingEntity;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalGroupMappingService;
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

        when(repository.findByClusterIgnoreCase("CONVENTIONAL OLD 1")).thenReturn(Optional.of(entity));

        assertThat(service.toGroup(" CONVENTIONAL OLD 1 ")).isEqualTo(Optional.of("Gas"));
        assertThat(service.toGroup("conventional old 1")).isEqualTo(Optional.of("Gas"));
    }

    @Test
    void toGroup_notFound_returnsEmpty() {
        when(repository.findByClusterIgnoreCase("ABCDEFG")).thenReturn(Optional.empty());
        assertThat(service.toGroup("abcdefg")).isEmpty();
    }

    @Test
    void toGroup_null_throwsNpe() {
        assertThatThrownBy(() -> service.toGroup(null))
                .isInstanceOf(NullPointerException.class);
    }
}
