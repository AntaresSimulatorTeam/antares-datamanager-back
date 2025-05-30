package com.rte_france.antares.datamanager_back.configuration.gaia;

import lombok.extern.log4j.Log4j2;
import org.springframework.ldap.core.AttributesMapper;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import java.lang.reflect.Field;

@SuppressWarnings("all")
@Log4j2
public class EmployeeMapper implements AttributesMapper<Employee> {


    public EmployeeMapper() {
    }

    @Override
    public Employee mapFromAttributes(Attributes attributes) {

        Employee employee = Employee.builder().build();
        for (Field field : employee.getClass().getDeclaredFields()) {
            if (!field.isSynthetic()) {
                field.setAccessible(true);
                switch (field.getName()) {
                    case PropertyManagement.RTE_EMPLOYEE_ATTRIBUTE_LAST_NAME:
                    case PropertyManagement.RTE_EMPLOYEE_ATTRIBUTE_FIRST_NAME:
                    case PropertyManagement.RTE_EMPLOYEE_ATTRIBUTE_EMAIL:
                    case PropertyManagement.RTE_EMPLOYEE_ATTRIBUTE_MOBILE_NUMBER:
                        LdapField ldapField = field.getAnnotation(LdapField.class);
                        if (attributes.get(ldapField.name()) != null) {
                            this.updateEmployeeField(employee, field, attributes, ldapField.name());
                        }
                        break;
                    case PropertyManagement.RTE_EMPLOYEE_ATTRIBUTE_NNI:
                        final String nniAttribute = PropertyManagement.RTE_LDAP_ATTRIBUTE_CN;
                        if (attributes.get(nniAttribute) != null) {
                            this.updateEmployeeField(employee, field, attributes, nniAttribute);
                        }
                        break;
                    default:
                        if (attributes.get(field.getName()) != null) {
                            this.updateEmployeeField(employee, field, attributes, field.getName());
                        }
                }
            }
        }

        return employee;
    }

    private void updateEmployeeField(Employee employee, Field field, Attributes attributes, String ldapFieldName) {
        try {
            field.set(employee, attributes.get(ldapFieldName).get());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            log.error("Erreur lors de la maj du champs " + field.getName() + " de l'utilisateur " + employee.getCn(), e);
        } catch (NamingException e) {
            e.printStackTrace();
            log.error("Erreur lors de la lecture du champs " + ldapFieldName + " de LDAP ", e);
        }
    }
}
