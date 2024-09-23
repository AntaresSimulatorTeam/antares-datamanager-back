package com.rte_france.antares.datamanager_back.configuration;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

@Slf4j
@Configuration
@Service
public class SftpDownloadService {

    private final SftpRemoteFileTemplate sftpRemoteFileTemplate;

    private final AntaressDataManagerProperties antaressDataManagerProperties;


    @Autowired
    public SftpDownloadService(SessionFactory<SftpClient.DirEntry> sftpSessionFactory, AntaressDataManagerProperties antaressDataManagerProperties) {
        this.sftpRemoteFileTemplate = new SftpRemoteFileTemplate(sftpSessionFactory);
        this.antaressDataManagerProperties = antaressDataManagerProperties;
    }

    @Scheduled(fixedDelayString = "${antares.datamanager.synchronize.data.scheduler_delay.in.milliseconds}")
    public void downloadDirectoryRecursively() {
        log.info("Téléchargement du répertoire distant : " + antaressDataManagerProperties.getDataRemoteDirectory());
        this.sftpRemoteFileTemplate.execute(session -> {
            try {
                downloadRecursive(session, antaressDataManagerProperties.getDataRemoteDirectory(), antaressDataManagerProperties.getDataLocalDirectoryStorage());
            } catch (Exception e) {
                throw new TechnicalAntaresDataMangerException("Erreur lors de la synchronisation des données PEGASE " + e.getMessage());
            }
            return null;
        });
    }

    private void downloadRecursive(Session<SftpClient.DirEntry> session, String remoteDir, String localDir) throws Exception {
        // Créer le répertoire local s'il n'existe pas
        File localDirectory = new File(localDir);
        if (!localDirectory.exists()) {
            localDirectory.mkdirs();
        }

        // Lister les fichiers et répertoires distants
        SftpClient.DirEntry[] files = session.list(remoteDir);

        for (SftpClient.DirEntry entry : files) {
            String filename = entry.getFilename();
            if (".".equals(filename) || "..".equals(filename)) {
                continue; // Ignorer les répertoires "." et ".."
            }

            String remoteFilePath = remoteDir + antaressDataManagerProperties.getPathDelimiter() + filename;
            File localFile = new File(localDir + antaressDataManagerProperties.getPathDelimiter() + filename);

            if (entry.getAttributes().isDirectory()) {
                // Si c'est un répertoire, parcourir récursivement
                downloadRecursive(session, remoteFilePath, localFile.getAbsolutePath());
            } else {
                // Si c'est un fichier, le télécharger
                log.info("Téléchargement du fichier : " + remoteFilePath);
                try (OutputStream os = new FileOutputStream(localFile)) {
                    session.read(remoteFilePath, os);
                }
            }
        }
    }
}
