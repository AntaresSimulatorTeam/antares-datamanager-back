package com.rte_france.antares.datamanager_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoDto {
    String nni;
    String firstName;
    String lastName;
}
