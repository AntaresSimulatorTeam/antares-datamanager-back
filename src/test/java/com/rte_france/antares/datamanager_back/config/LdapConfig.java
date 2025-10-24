package com.rte_france.antares.datamanager_back.config;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
public class LdapConfig {

    @Autowired
    private InMemoryDirectoryServer directoryServer; // Injecté automatiquement par Spring Boot

    @Value("${spring.ldap.embedded.base-dn}")
    private String base;

    @Bean
    public LdapContextSource ldapTestContextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        int port = directoryServer.getListenPort(); // Le port réellement utilisé par l'instance LDAP
        contextSource.setUrl("ldap://localhost:" + port);
        contextSource.setBase(base);
        contextSource.afterPropertiesSet();
        return contextSource;
    }

    @Bean
    public LdapTemplate ldapTemplate() {
        return new LdapTemplate(ldapTestContextSource());
    }
}
