package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.configuration.gaia.Employee;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LdapMapper {

    public List<UserInfoDto> toUsersDto(List<Employee> employees) {
        if (employees == null) {
            return Collections.emptyList();
        }
        return employees.stream()
                .map(this::toUserDto)
                .toList();
    }

    public UserInfoDto toUserDto(Employee employee) {
        return UserInfoDto.builder()
                .nni(employee.getNni())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .build();
    }

    public UserInfoDto toLdapUserModel(Employee userFromLdap) {
        return UserInfoDto.builder()
                .nni(userFromLdap.getNni())
                .firstName(userFromLdap.getFirstName())
                .lastName(userFromLdap.getLastName())
                .email(userFromLdap.getEmail())
                .build();
    }

    public List<UserInfoDto> toLdapUserModels(List<Employee> userList) {
        if (userList == null) {
            return Collections.emptyList();
        }
        return userList.stream()
                .map(this::toLdapUserModel)
                .collect(Collectors.toList());
    }
}
