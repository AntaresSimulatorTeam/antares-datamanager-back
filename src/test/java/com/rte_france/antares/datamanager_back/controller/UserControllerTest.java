package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.configuration.gaia.Employee;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.mapper.LdapMapper;
import com.rte_france.antares.datamanager_back.service.impl.LdapClientEmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 class UserControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    LdapClientEmployeeService ldapClientEmployeeService;
    @MockBean
    LdapMapper ldapMapper;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }
    @Test
    void getUserByNniReturnsUserWhenFound() throws Exception {
        // Given
        String nni = "12345";
        UserInfoDto user = UserInfoDto.builder().nni(nni).build();
        Employee employee = Employee.builder().nni(nni).cn("John Doe").build();
        when(ldapClientEmployeeService.getUserByNni(nni)).thenReturn(employee);
        when(ldapMapper.toUserDto(employee)).thenReturn(user);

        // When
        mockMvc.perform(get("/v1/user/{nni}", nni)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                // Then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nni").value(nni))
                .andDo(MockMvcResultHandlers.print());

        verify(ldapClientEmployeeService, times(1)).getUserByNni(nni);
        verify(ldapMapper, times(1)).toUserDto(employee);
    }

    @Test
    void getUserByNniReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        // Given
        String nni = "12345";
        when(ldapClientEmployeeService.getUserByNni(nni)).thenReturn(null);
        when(ldapMapper.toUserDto(null)).thenReturn(null);

        // When
        mockMvc.perform(get("/v1/user/{nni}", nni)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                // Then
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        verify(ldapClientEmployeeService, times(1)).getUserByNni(nni);
        verify(ldapMapper, times(1)).toUserDto(null);
    }

    @Test
    void getUsersByListNniReturnsListOfUsers() throws Exception {
        // Given
        List<String> nniList = List.of("12345", "67890");
        UserInfoDto user1 = UserInfoDto.builder().nni("12345").build();
        Employee employee1 = Employee.builder().nni("12345").cn("John Doe").build();
        when(ldapClientEmployeeService.getUserByNni("12345")).thenReturn(employee1);
        when(ldapMapper.toUserDto(employee1)).thenReturn(user1);

        // When
        mockMvc.perform(post("/v1/user/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"12345\", \"67890\"]")
                        .accept(MediaType.APPLICATION_JSON))
                // Then
                .andExpect(status().isOk())
                .andDo(MockMvcResultHandlers.print());

        verify(ldapClientEmployeeService, times(1)).getUsersByListNni(nniList);
    }

    @Test
    void getUsersByListNniReturnsEmptyListWhenNoUsersFound() throws Exception {
        // Given
        List<String> nniList = List.of("12345", "67890");
        when(ldapClientEmployeeService.getUsersByListNni(nniList)).thenReturn(List.of());
        when(ldapMapper.toUsersDto(List.of())).thenReturn(List.of());

        // When
        mockMvc.perform(post("/v1/user/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"12345\", \"67890\"]")
                        .accept(MediaType.APPLICATION_JSON))
                // Then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0))
                .andDo(MockMvcResultHandlers.print());

        verify(ldapClientEmployeeService, times(1)).getUsersByListNni(nniList);
        verify(ldapMapper, times(1)).toUsersDto(List.of());
    }
}
