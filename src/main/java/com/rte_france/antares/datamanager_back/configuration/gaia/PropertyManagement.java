package com.rte_france.antares.datamanager_back.configuration.gaia;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Manage Environment variables
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PropertyManagement {

    public static final String RTE_LDAP_URLS = "${rte.ldap.urls}";
    public static final String RTE_LDAP_BASE = "${rte.ldap.base}";
    public static final String RTE_LDAP_USERNAME = "${rte.ldap.username}";
    public static final String RTE_LDAP_PASSWORD = "${rte.ldap.password}";


    public static final String[] RTE_LDAP_ATTR = new String[]{"uid", "cn", "sn", "givenName", "mail", "objectClass",
            "manager", "title", "l", "telephoneNumber", "facsimileTelephoneNumber", "mobile", "roomNumber",
            "rteFloor", "rteRHO", "rteRHOLib", "departmentDN", "employeeType", "businessCategory", "rteUsrEtablEmpl",
            "rteUsrCtrtType", "company", "rteMetier", "departmentNumber", "rteUsrEtablCP", "rteDomainProLib",
            "rteUsrEtablVille", "rteDomainPro", "fullName", "rteUsrCtrtTimeType", "groupMembership",
            "juridicalDepartmentNumber", "rteUsrEtablPays", "rteMetierLib", "sAMAccountName"};

    public static final String RTE_LDAP_PERSON_OBJECT = "person";


    public static final String RTE_LDAP_ATTRIBUTE_SN = "sn";
    public static final String RTE_LDAP_ATTRIBUTE_CN = "cn";

    public static final String RTE_LDAP_ATTRIBUTE_GIVEN_NAME = "givenName";
    public static final String RTE_LDAP_ATTRIBUTE_MAIL = "mail";
    public static final String RTE_LDAP_ATTRIBUTE_MOBILE = "mobile";
    public static final String RTE_LDAP_ATTRIBUTE_OBJECT_CLASS = "objectclass";
    public static final String RTE_EMPLOYEE_ATTRIBUTE_LAST_NAME = "lastName";
    public static final String RTE_EMPLOYEE_ATTRIBUTE_FIRST_NAME = "firstName";
    public static final String RTE_EMPLOYEE_ATTRIBUTE_EMAIL = "email";
    public static final String RTE_EMPLOYEE_ATTRIBUTE_MOBILE_NUMBER = "mobileNumber";
    public static final String RTE_EMPLOYEE_ATTRIBUTE_NNI = "nni";
    public static final String RTE_LDAP_ATTRIBUTE_SAM_ACCOUNT_NAME = "sAMAccountName";
}