package com.rte_france.antares.datamanager_back.configuration.gaia;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Manage Environment variables
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PropertyManagement {

    public static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";
    public static final String JAVAX_NET_SSL_TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";

    public static final String RTE_LDAP_URLS = "${rte.ldap.urls}";
    public static final String RTE_LDAP_BASE = "${rte.ldap.base}";
    public static final String RTE_LDAP_USERNAME = "${rte.ldap.username}";
    public static final String RTE_LDAP_PASSWORD = "${rte.ldap.password}";
    public static final String RTE_AAA_TRUST_STORE_PATH = "${rte.aaa.trustStorePath}";
    public static final String RTE_AAA_TRUST_STORE_PASSWORD = "${rte.aaa.trustStorePwd}";
    public static final String RTE_ENV = "${rte.env}";
    public static final String RTE_ENV_DCH = "DCH";


    public static final String[] RTE_LDAP_ATTR = new String[]{"uid", "cn", "sn", "givenName", "mail", "objectClass",
            "manager", "title", "l", "telephoneNumber", "facsimileTelephoneNumber", "mobile", "roomNumber",
            "rteFloor", "rteRHO", "rteRHOLib", "departmentDN", "employeeType", "businessCategory", "rteUsrEtablEmpl",
            "rteUsrCtrtType", "company", "rteMetier", "departmentNumber", "rteUsrEtablCP", "rteDomainProLib",
            "rteUsrEtablVille", "rteDomainPro", "fullName", "rteUsrCtrtTimeType", "groupMembership",
            "juridicalDepartmentNumber", "rteUsrEtablPays", "rteMetierLib", "sAMAccountName"};

    public static final String RTE_LDAP_PERSON_OBJECT = "person";
    public static final String RTE_LDAP_GROUP_OBJECT = "group";
    public static final String RTE_LDAP_RTE_GROUP_OBJECT = "rteGroup";
    public static final String RTE_LDAP_USER_OBJECT = "user";
    public static final String RTE_LDAP_RTE_PERSON_OBJECT = "rtePerson";

    public static final String RTE_LDAP_ATTRIBUTE_SN = "sn";
    public static final String RTE_LDAP_ATTRIBUTE_CN = "cn";
    public static final String RTE_LDAP_ATTRIBUTE_FULL_NAME = "fullName";
    public static final String RTE_LDAP_ATTRIBUTE_MEMBER_OF = "memberOf";
    public static final String RTE_LDAP_ATTRIBUTE_MEMBER_OF_WITH_FILTER = "memberOf:1.2.840.113556.1.4.1941:";

    public static final String RTE_LDAP_ATTRIBUTE_GIVEN_NAME = "givenName";
    public static final String RTE_LDAP_ATTRIBUTE_MAIL = "mail";
    public static final String RTE_LDAP_ATTRIBUTE_MOBILE = "mobile";
    public static final String RTE_LDAP_ATTRIBUTE_OBJECT_CLASS = "objectclass";
    public static final String RTE_LDAP_ATTRIBUTE_RTE_RHO_LIB = "rteRHOLib";
    public static final String RTE_LDAP_ATTRIBUTE_MEMBER = "member";
    public static final String RTE_EMPLOYEE_ATTRIBUTE_LAST_NAME = "lastName";
    public static final String RTE_EMPLOYEE_ATTRIBUTE_FIRST_NAME = "firstName";
    public static final String RTE_EMPLOYEE_ATTRIBUTE_EMAIL = "email";
    public static final String RTE_EMPLOYEE_ATTRIBUTE_MOBILE_NUMBER = "mobileNumber";
    public static final String RTE_EMPLOYEE_ATTRIBUTE_NNI = "nni";
    public static final String RTE_LDAP_ATTRIBUTE_SAM_ACCOUNT_NAME = "sAMAccountName";
    public static final String BASE_GSL_GROUP_IN_APSU =",OU=R0_HORUS_APSU_Acces,OU=R0_HORUS_APSU,OU=R0_HORUS,OU=Region_0,OU=Regions,DC=tcd,DC=local";
}