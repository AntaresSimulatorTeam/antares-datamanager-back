package com.rte_france.antares.datamanager_back.configuration;

import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

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

    public File downloadFile(String remoteFilePath) {
        // Extract the filename from the remote file path
        String filename = Paths.get(remoteFilePath).getFileName().toString();

        // Construct the local file path using the extracted filename
        String localFilePath = "/home/elazaarmou/pegase_data_2" + File.separator + filename; // replace "localDirectoryPath" with your local directory path

        File localFile = new File(localFilePath);
        try (OutputStream outputStream = new FileOutputStream(localFile)) {
            this.sftpRemoteFileTemplate.execute(session -> {
                session.read(remoteFilePath, outputStream);
                return null;
            });
        } catch (IOException e) {
            log.error("Error while retrieving the file: " + remoteFilePath, e);
        }
        return localFile;
    }

    public List<FsTrajectoryDTO> listFsTrajectoryByType(TrajectoryType trajectoryType, String area) {
        try {
            return this.sftpRemoteFileTemplate.execute(session -> {
                SftpClient.DirEntry[] entries = session.list(antaressDataManagerProperties.getDataRemoteDirectory() + getDirectoryByTrajectoryType(trajectoryType, area));
                return Arrays.stream(entries)
                        .filter(dirEntry -> !dirEntry.getAttributes().isDirectory())
                        .map(file -> FsTrajectoryDTO.builder()
                                .fileName(file.getFilename())
                                .lastModifiedDate(LocalDateTime.ofInstant(file.getAttributes().getModifyTime().toInstant(), ZoneId.systemDefault()))
                                .type(trajectoryType.name())
                                .build())
                        .toList();
            });
        } catch (Exception e) {
            throw new TechnicalAntaresDataMangerException("Erreur lors de la récupération des fichiers PEGASE :  " + e.getMessage());
        }

    }

    //@Scheduled(cron = "0 0 6 * * *")
    public void downloadDirectoryRecursively() {
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

            String remoteFilePath = remoteDir + File.separator + filename;
            File localFile = new File(localDir + File.separator + filename);
            if (entry.getAttributes().isDirectory()) {
                // Si c'est un répertoire, parcourir récursivement
                downloadRecursive(session, remoteFilePath, localFile.getAbsolutePath());
            } else {
                // Si c'est un fichier, le télécharger
                log.info("Téléchargement du fichier : " + remoteFilePath + " derniere modification : " + entry.getAttributes().getModifyTime().toInstant());
                try (OutputStream os = new FileOutputStream(localFile)) {
                    session.read(remoteFilePath, os);
                }
            }
        }
    }

    private String getDirectoryByTrajectoryType(TrajectoryType trajectoryType, String area) {
        return switch (trajectoryType) {
            case AREA -> antaressDataManagerProperties.getAreaDirectory();
            case LINK -> antaressDataManagerProperties.getLinkDirectory();
            case THERMAL_COST -> antaressDataManagerProperties.getThermalCostDirectory();
            case THERMAL_CAPACITY ->
                    antaressDataManagerProperties.getThermalCapacityDirectory() + File.separator + area;
            case THERMAL_PARAMETER -> antaressDataManagerProperties.getThermalParameterDirectory();
            case LOAD, MISC ->
                    throw new IllegalArgumentException("No directory defined for TrajectoryType: " + trajectoryType);
            default -> throw new IllegalArgumentException("Invalid TrajectoryType: " + trajectoryType);
        };
    }
}
