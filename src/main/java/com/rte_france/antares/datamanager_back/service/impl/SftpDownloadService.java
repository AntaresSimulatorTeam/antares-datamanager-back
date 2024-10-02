package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.AreaDTO;
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
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@Service
public class SftpDownloadService {

    private final SftpRemoteFileTemplate sftpRemoteFileTemplate;

    private final AntaressDataManagerProperties antaressDataManagerProperties;


    public SftpDownloadService(SessionFactory<SftpClient.DirEntry> sftpSessionFactory, AntaressDataManagerProperties antaressDataManagerProperties) {
        this.sftpRemoteFileTemplate = new SftpRemoteFileTemplate(sftpSessionFactory);
        this.antaressDataManagerProperties = antaressDataManagerProperties;
    }

    public File downloadFile(String remoteFilePath) {
        // Extract the filename from the remote file path
        String filename = Paths.get(remoteFilePath).getFileName().toString();

        // Construct the local file path using the extracted filename
        String localFilePath = "/tmp" + File.separator + filename;
                // replace "localDirectoryPath" with your local directory path

        File localFile = new File(localFilePath);

        try (OutputStream outputStream = new FileOutputStream(localFile)) {
            this.sftpRemoteFileTemplate.execute(session -> {
                if (session.exists(remoteFilePath)) {
                    log.info("Downloading the file: " + remoteFilePath);
                    session.read(remoteFilePath, outputStream);
                } else {
                    throw new TechnicalAntaresDataMangerException("Error while retrieving the file: " + remoteFilePath);
                }
                return localFile;
            });
        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Technical Error : " + e.getMessage());
        }
        return localFile;
    }

    public List<FsTrajectoryDTO> listFsTrajectoryByType(TrajectoryType trajectoryType, String thermalCapacityArea) {
        try {
            return this.sftpRemoteFileTemplate.execute(session -> {
                SftpClient.DirEntry[] entries = session.list(antaressDataManagerProperties.getDataRemoteDirectory() + getDirectoryByTrajectoryType(trajectoryType, thermalCapacityArea));
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

    public List<AreaDTO> findThermalCapacityAreas() {
        try {
            return this.sftpRemoteFileTemplate.execute(session -> {
                SftpClient.DirEntry[] entries = session.list(antaressDataManagerProperties.getDataRemoteDirectory() + antaressDataManagerProperties.getThermalCapacityDirectory());
                return Arrays.stream(entries)
                        .filter(dirEntry -> dirEntry.getAttributes().isDirectory() && dirEntry.getFilename().matches("[a-zA-Z]+"))
                        .map(file -> AreaDTO.builder()
                                .name(file.getFilename())
                                .lastModifiedDate(LocalDateTime.ofInstant(file.getAttributes().getModifyTime().toInstant(), ZoneId.systemDefault()))
                                .build())
                        .toList();

            });
        } catch (Exception e) {
            throw new TechnicalAntaresDataMangerException("Erreur lors de la récupération des fichiers PEGASE :  " + e.getMessage());
        }

    }

    public String getDirectoryByTrajectoryType(TrajectoryType trajectoryType, String thermalCapacityArea) {
        return switch (trajectoryType) {
            case AREA -> antaressDataManagerProperties.getAreaDirectory();
            case LINK -> antaressDataManagerProperties.getLinkDirectory();
            case THERMAL_COST -> antaressDataManagerProperties.getThermalCostDirectory();
            case THERMAL_CAPACITY ->
                    antaressDataManagerProperties.getThermalCapacityDirectory() + File.separator + thermalCapacityArea;
            case THERMAL_PARAMETER -> antaressDataManagerProperties.getThermalParameterDirectory();
            case LOAD, MISC ->
                    throw new IllegalArgumentException("No directory defined for TrajectoryType: " + trajectoryType);
            default -> throw new IllegalArgumentException("Invalid TrajectoryType: " + trajectoryType);
        };
    }
}
