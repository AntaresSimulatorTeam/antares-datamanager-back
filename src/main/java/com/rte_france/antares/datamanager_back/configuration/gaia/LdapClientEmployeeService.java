package com.rte_france.antares.datamanager_back.configuration.gaia;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.ContainerCriteria;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.rte_france.antares.datamanager_back.configuration.gaia.PropertyManagement.*;
import static org.springframework.ldap.query.LdapQueryBuilder.query;

@Service
@RequiredArgsConstructor
public class LdapClientEmployeeService {

    private final LdapTemplate ldapTemplate;

    public List<Employee> getPersonNamesByLastName(String lastName) {

        LdapQuery query = query()
                .attributes(RTE_LDAP_ATTR)
                .where(RTE_LDAP_ATTRIBUTE_OBJECT_CLASS).is(RTE_LDAP_PERSON_OBJECT)
                .and(RTE_LDAP_ATTRIBUTE_SN).like(lastName);

        return ldapTemplate.search(query, new EmployeeMapper());
    }

    public List<Employee> getRtePersons() {
        LdapQuery query = query()
                .attributes(RTE_LDAP_ATTR)
                .where(RTE_LDAP_ATTRIBUTE_OBJECT_CLASS).is("rtePerson")
                .and(RTE_LDAP_ATTRIBUTE_MAIL).isPresent()
                .and(RTE_LDAP_ATTRIBUTE_RTE_RHO_LIB).isPresent()
                .and(RTE_LDAP_ATTRIBUTE_RTE_RHO_LIB).not().is("Non Affectes");

        return ldapTemplate.search(query, new EmployeeMapper());
    }

    /**
     * Get list of User by their nni/name from snp and dch
     *
     * @param nameList list of name
     * @return list of User
     */
    public List<Employee> getUsersByName(List<String> nameList) {
        String[] nameArray = nameList.toArray(new String[0]);

        ContainerCriteria initQuery = query()
                .where(RTE_LDAP_ATTRIBUTE_OBJECT_CLASS).is(RTE_LDAP_PERSON_OBJECT);
        ContainerCriteria cnCriteria = query().where(RTE_LDAP_ATTRIBUTE_CN).is(nameArray[0]);

        Arrays.stream(nameArray).skip(1).forEach(name -> cnCriteria.or(RTE_LDAP_ATTRIBUTE_CN).is(name));

        initQuery.and(cnCriteria);

        return ldapTemplate.search(initQuery, new EmployeeMapper());
    }

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
     * Get list of User by their nni from snp and dch
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
