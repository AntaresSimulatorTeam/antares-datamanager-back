package com.rte_france.antares.datamanager_back.configuration.gaia;

import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Getter
@Configuration
public class GaiaConfig {

    @Value(PropertyManagement.RTE_LDAP_URLS)
    private String ldapUrl;

    @Value(PropertyManagement.RTE_LDAP_BASE)
    private String ldapBase;

    @Value(PropertyManagement.RTE_LDAP_USERNAME)
    private String ldapUsername;

    @Value(PropertyManagement.RTE_LDAP_PASSWORD)
    private String ldapPassword;

    @Bean(name = "gaiaContextSource") // ou "ldapContextSourceTest"
    public LdapContextSource ldapContextSource() throws TechnicalException {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapUrl);
        contextSource.setBase(ldapBase);
        contextSource.setUserDn(ldapUsername);
        contextSource.setPassword(ldapPassword);
        contextSource.afterPropertiesSet();

        return contextSource;
    }

    @Bean(name = "gaiaTemplate") // ou "ldapContextSourceTest"
    public LdapTemplate ldapTemplate() throws TechnicalException {
        return new LdapTemplate(ldapContextSource());
    }

}
