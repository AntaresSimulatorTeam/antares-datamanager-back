package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.mapper.LdapMapper;
import com.rte_france.antares.datamanager_back.service.impl.LdapClientEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final LdapClientEmployeeService ldapClientEmployeeService;
    private final LdapMapper ldapMapper;


    @GetMapping("/{nni}")
    public ResponseEntity<UserInfoDto> getUserByNni(@PathVariable String nni) {
        UserInfoDto user = ldapMapper.toUserDto(ldapClientEmployeeService.getUserByNni(nni));
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @PostMapping("/list")
    public ResponseEntity<List<UserInfoDto>> getUsersByListNni(@RequestBody List<String> listNni) {
        List<UserInfoDto> users = ldapMapper.toUsersDto(ldapClientEmployeeService.getUsersByListNni(listNni));
        return ResponseEntity.ok(users);
    }
}
