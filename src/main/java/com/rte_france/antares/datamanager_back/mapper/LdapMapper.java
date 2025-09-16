package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.configuration.gaia.Employee;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import lombok.*;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LdapMapper {

    public static List<UserInfoDto> toUsersDto(List<Employee> employees) {
        if (employees == null) {
            return Collections.emptyList();
        }
        return employees.stream()
                .map(LdapMapper::toUserDto)
                .toList();
    }

    public static UserInfoDto toUserDto(Employee employee) {
        if( employee == null) {
            return null;
        }
        return UserInfoDto.builder()
                .nni(employee.getNni())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .build();
    }
}
