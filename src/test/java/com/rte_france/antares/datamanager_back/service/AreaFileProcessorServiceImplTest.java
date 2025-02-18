package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.AreaConfigRepository;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.AreaFileProcessorServiceImpl;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AreaFileProcessorServiceImplTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private AreaConfigRepository areaConfigRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @InjectMocks
    private AreaFileProcessorServiceImpl areaFileProcessorService;

    private static Path tempFile;

    @BeforeEach
    public void setup(@TempDir Path tempDir) throws IOException {
        MockitoAnnotations.openMocks(this);

        tempFile = tempDir.resolve("testFile.xlsx");
        try (var outputStream = Files.newOutputStream(tempFile)) {
            outputStream.write(generateTestExcelFile());
        }
    }

    private static byte[] generateTestExcelFile() throws IOException {
        var outputStream = new ByteArrayOutputStream();
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2030-2031");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("Test Data");
            workbook.write(outputStream);
        }
        return outputStream.toByteArray();
    }

    @Test
    void processAreaFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        var trajectoryEntity = mock(TrajectoryEntity.class);
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any()))
                .thenReturn(Optional.of(trajectoryEntity));

        areaFileProcessorService.processAreaFile(tempFile, "2030-2031");

        verify(trajectoryRepository, times(1)).save(any());
        verify(areaConfigRepository, times(1)).saveAll(any());
    }

    @Test
    void processAreaFile_whenTrajectoryDoesNotExist() throws IOException {
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());

        areaFileProcessorService.processAreaFile(tempFile, "2030-2031");

        verify(trajectoryRepository, times(1)).save(any());
        verify(areaConfigRepository, times(1)).saveAll(any());
    }
}
