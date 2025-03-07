package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.repository.LinkRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.LinkFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import com.rte_france.antares.datamanager_back.util.ExcelFileValidators.ColumnsEnums.LinksColumns;
import com.rte_france.antares.datamanager_back.util.ExcelFileValidators.LinksValidator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.MessageSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LinkFileProcessorServiceImplTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private WarningMessageService warningMessageService;

    @InjectMocks
    private LinkFileProcessorServiceImpl linkFileProcessorService;

    private Path tempFile;

    @TempDir
    Path tempDir;

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

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < LinksColumns.values().length; i++) {
                headerRow.createCell(i).setCellValue(LinksColumns.values()[i].getDisplayName());
            }
            Row row = sheet.createRow(1);
            for (int i = 0; i < mockValues.length; i++) {
                if (mockValues[i] instanceof Number) {
                    row.createCell(i).setCellValue(((Number) mockValues[i]).doubleValue());
                } else {
                    row.createCell(i).setCellValue(mockValues[i].toString());
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


    @Test
    void testProcessLinkFileWithWarning() throws IOException {


        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(
                tempDir,
                "TestFile.xlsx",  // File name
                List.of("2032-2033", "EmptySheet"),  // Sheet names
                List.of(
                        List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                                "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                                "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                                "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                                "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),  // Headers for sheet 1 (with data)
                        List.of()
                ),
                List.of(
                        List.of(
                                List.of("Area1/Area2", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", "FALSE", "TRUE", "FALSE"),
                                List.of("Area3/Area4", 10, 20, 30, 40, 50, 60, 70, 80, "TRUE", "FALSE", "TRUE", "FALSE")
                        ),
                        List.of()
        ));


        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("TestFile.xlsx");
        trajectoryEntity.setVersion(1);
       // trajectoryEntity.setWarningMessage(Collections.singletonList("message"));

        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc("TestFile.xlsx"))
                .thenReturn(Optional.of(trajectoryEntity));


        linkFileProcessorService.processLinkFile(tempFile, "2032-2033");

        verify(warningMessageService, times(3)).getMessage(anyString());


    }

}
