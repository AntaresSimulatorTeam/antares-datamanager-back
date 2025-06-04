package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.gaia.Employee;
import com.rte_france.antares.datamanager_back.service.impl.LdapClientEmployeeService;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
@SpringBootTest
class LdapGaiaClientEmployeeServiceTest {

    @Autowired
    private LdapClientEmployeeService ldapClientEmployeeService;

    @Test
    void getUserById_withUserId_shouldReturnUser() {

        //Given
        final String userId = "CG66752T";
        //When
        Employee employee = ldapClientEmployeeService.getUserByNni(userId);
        //Then
        assertThat(employee).extracting(Employee::getCn, Employee::getFullName)
                .contains("CG66752T", "GAY Charly");

    }

    @Test
    void getUsersByListNni_withListNni() {

        //Given
        List<String> listNni = Arrays.asList("LL75140T", "CG66752T");

        //When
        List<Employee> employees = ldapClientEmployeeService.getUsersByListNni(listNni);

        //Then
        org.assertj.core.api.Assertions.assertThat(employees)
                .extracting(Employee::getFullName, Employee::getNni)
                .contains(
                        Tuple.tuple("Lyes LAKEHAL", "LL75140T"),
                        Tuple.tuple("GAY Charly", "CG66752T"));
    }
}
