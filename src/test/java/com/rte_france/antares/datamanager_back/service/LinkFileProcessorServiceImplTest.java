package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.repository.LinkRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.LinkFileProcessorServiceImpl;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LinkFileProcessorServiceImplTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @InjectMocks
    private LinkFileProcessorServiceImpl linkFileProcessorService;

    private Path tempFile;

    @BeforeEach
    public void setup(@TempDir Path tempDir) throws IOException {
        MockitoAnnotations.openMocks(this);

        tempFile = tempDir.resolve("links_BP23_A_ref.xlsx");
        try (var outputStream = Files.newOutputStream(tempFile)) {
            outputStream.write(generateTestExcelFile());
        }
    }

    private static byte[] generateTestExcelFile() throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2030-2031");
            sheet.createRow(1).createCell(1).setCellValue(100.5);
            workbook.createSheet("parameters");

            Object[] mockValues = { "Link1", 200.0, 150.0, 120.0, 100.0, 80.0, 60.0, 50.0, 30.0,
                    "true", "false", "true", "false" };

            var row = sheet.createRow(1);
            for (var i = 0; i < mockValues.length; i++) {
                switch (mockValues[i]) {
                    case Number n -> row.createCell(i).setCellValue(n.doubleValue());
                    default -> row.createCell(i).setCellValue(mockValues[i].toString());
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }


    @Test
    void processLinkFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);

        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));

        linkFileProcessorService.processLinkFile(tempFile,"2030-2031");

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processLinkFile_whenTrajectoryDoesNotExist() throws IOException {
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());

        linkFileProcessorService.processLinkFile(tempFile,"2030-2031");

        verify(trajectoryRepository, times(1)).save(any());
    }
}
