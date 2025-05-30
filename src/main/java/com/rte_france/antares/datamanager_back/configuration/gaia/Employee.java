package com.rte_france.antares.datamanager_back.configuration.gaia;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;

import javax.naming.Name;


@Entry(objectClasses = { "person", "top" })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee {
    @Id
    private Name dn;
    private String uid;
    private String cn;
    @LdapField(name = PropertyManagement.RTE_LDAP_ATTRIBUTE_CN)
    @Attribute(name = PropertyManagement.RTE_LDAP_ATTRIBUTE_SAM_ACCOUNT_NAME)
    private String nni;
    @LdapField(name = PropertyManagement.RTE_LDAP_ATTRIBUTE_SN)
    @Attribute(name = PropertyManagement.RTE_LDAP_ATTRIBUTE_SN)
    private String lastName;
    @LdapField(name = PropertyManagement.RTE_LDAP_ATTRIBUTE_GIVEN_NAME)
    @Attribute(name = PropertyManagement.RTE_LDAP_ATTRIBUTE_GIVEN_NAME)
    private String firstName;
    @LdapField(name = PropertyManagement.RTE_LDAP_ATTRIBUTE_MAIL)
    private String email;
    private String title;
    private String l;
    private String telephoneNumber;
    private String facsimileTelephoneNumber;
    @LdapField(name = PropertyManagement.RTE_LDAP_ATTRIBUTE_MOBILE)
    private String mobileNumber;
    private String rteRHOLib;
    private String employeeType;
    private String fullName;
}
