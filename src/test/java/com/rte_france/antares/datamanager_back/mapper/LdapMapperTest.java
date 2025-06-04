package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.configuration.gaia.Employee;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class LdapMapperTest {
    @Test
    void toUsersDtoReturnsEmptyListWhenInputIsNull() {
        List<UserInfoDto> result = LdapMapper.toUsersDto(null);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void toUsersDtoReturnsEmptyListWhenInputIsEmpty() {
        List<UserInfoDto> result = LdapMapper.toUsersDto(Collections.emptyList());
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void toUsersDtoMapsEmployeesToUserInfoDtosCorrectly() {
        Employee employee1 = Employee.builder()
                .nni("1234555")
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();
        Employee employee2 = Employee.builder()
                .nni("12345")
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .build();
        List<Employee> employees = List.of(employee1, employee2);

        List<UserInfoDto> result = LdapMapper.toUsersDto(employees);

        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("1234555", result.getFirst().getNni());
        Assertions.assertEquals("John", result.getFirst().getFirstName());
        Assertions.assertEquals("Doe", result.getFirst().getLastName());
        Assertions.assertEquals("john.doe@example.com", result.getFirst().getEmail());
        Assertions.assertEquals("12345", result.get(1).getNni());
        Assertions.assertEquals("Jane", result.get(1).getFirstName());
        Assertions.assertEquals("Smith", result.get(1).getLastName());
        Assertions.assertEquals("jane.smith@example.com", result.get(1).getEmail());
    }

    @Test
    void toUserDtoMapsEmployeeToUserInfoDtoCorrectly() {
        Employee employee = Employee.builder()
                .nni("12345")
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        UserInfoDto result = LdapMapper.toUserDto(employee);

        Assertions.assertEquals("12345", result.getNni());
        Assertions.assertEquals("John", result.getFirstName());
        Assertions.assertEquals("Doe", result.getLastName());
        Assertions.assertEquals("john.doe@example.com", result.getEmail());
    }

}
