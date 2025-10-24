package com.rte_france.antares.datamanager_back.service.user;

import com.rte_france.antares.datamanager_back.configuration.gaia.Employee;
import com.rte_france.antares.datamanager_back.configuration.gaia.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.ContainerCriteria;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.rte_france.antares.datamanager_back.configuration.gaia.PropertyManagement.*;
import static org.springframework.ldap.query.LdapQueryBuilder.query;

@Service
@RequiredArgsConstructor
public class LdapClientEmployeeService {

    private final LdapTemplate ldapTemplate;

    /**
     * Get User  by id
     *
     * @return user Employee
     */
    public Employee getUserByNni(String nni) {
        try {
            return ldapTemplate.findOne(
                    query()
                            .attributes(RTE_LDAP_ATTR)
                            .where(RTE_LDAP_ATTRIBUTE_OBJECT_CLASS).is(RTE_LDAP_PERSON_OBJECT)
                            .and(RTE_LDAP_ATTRIBUTE_CN).is(nni), Employee.class);
        } catch (EmptyResultDataAccessException emptyResultDataAccessException) {
            return null;
        }
    }


    /**
     * Get list of User by their nni from GAIA
     *
     * @param listNni list of nni
     * @return list of User
     */
    public List<Employee> getUsersByListNni(List<String> listNni) {
        String[] nameArray = listNni.toArray(new String[0]);
        final String nniField = RTE_LDAP_ATTRIBUTE_CN;
        ContainerCriteria initQuery = query()
                .where(RTE_LDAP_ATTRIBUTE_OBJECT_CLASS).is(RTE_LDAP_PERSON_OBJECT);
        ContainerCriteria cnCriteria = query().where(nniField).is(nameArray[0]);

        Arrays.stream(nameArray).skip(1).forEach(name -> cnCriteria.or(nniField).is(name));

        initQuery.and(cnCriteria);

        return ldapTemplate.search(initQuery, new EmployeeMapper());
    }
}
