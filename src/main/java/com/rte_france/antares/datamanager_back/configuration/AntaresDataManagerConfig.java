package com.rte_france.antares.datamanager_back.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
@RequiredArgsConstructor
@Configuration
public class AntaresDataManagerConfig {

    private final AntaressDataManagerProperties antaressDataManagerProperties;



    @Bean
    // 1. Configuration de la session SFTP pour se connecter au serveur distant
    public DefaultSftpSessionFactory sftpSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(antaressDataManagerProperties.getDataHost());
        factory.setPort(22);
        factory.setUser(antaressDataManagerProperties.getDataHostUsername());
        factory.setPassword(antaressDataManagerProperties.getDataHostPassword());
        factory.setAllowUnknownKeys(true);  // à utiliser uniquement en développement
        return factory;
    }
}