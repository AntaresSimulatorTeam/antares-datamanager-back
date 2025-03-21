package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.repository.LinkRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningMessageRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningCode;
import com.rte_france.antares.datamanager_back.service.impl.LinkFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LinkFileProcessorServiceImplTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private WarningMessageRepository warningMessageRepository;

    @Mock
    private WarningMessageService warningMessageService;

    @Mock
    private UserService userService;

    @InjectMocks
    private LinkFileProcessorServiceImpl linkFileProcessorService;

    private Path tempFile;

    private TrajectoryEntity trajectoryEntity;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup(@TempDir Path tempDir) throws IOException {
        MockitoAnnotations.openMocks(this);

        tempFile = tempDir.resolve("links_BP23_A_ref.xlsx");
        try (var outputStream = Files.newOutputStream(tempFile)) {
            outputStream.write(generateTestExcelFile());
        }

         trajectoryEntity = TrajectoryEntity.builder()
                .id(1)
                .areaConfigEntities(List.of(AreaConfigEntity.builder()
                                .area(AreaEntity.builder().name("FR").build()).build(),
                        AreaConfigEntity.builder()
                                .area(AreaEntity.builder().name("CH").build()).build(),
                        AreaConfigEntity.builder()
                                .area(AreaEntity.builder().name("IT").build()).build()))
                .build();
        when(trajectoryRepository.findByTypeAndStudyId(any(), any())).thenReturn(List.of(trajectoryEntity));

        when(warningMessageService.getMessage(anyString(), any())).thenReturn("Expected message");
    }

    @Test
    void processLinkFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());

        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));

        linkFileProcessorService.processLinkFile(tempFile, "2030-2031", 1);

        verify(trajectoryRepository, times(1)).save(any());
        verify(warningMessageRepository, times(1)).saveAll(any());
    }

    @Test
    void processLinkFile_whenTrajectoryDoesNotExist() throws IOException {
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());

        linkFileProcessorService.processLinkFile(tempFile, "2030-2031", 1);

        verify(trajectoryRepository, times(1)).save(any());
    }

@Test
void testProcessLinkFileWithWarning() throws IOException {
    tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(
            tempDir,
            "TestFile.xlsx",
            List.of("2032-2033", "EmptySheet"),
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
                            List.of("FR-CH", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", "FALSE", "TRUE", "FALSE"),
                            List.of("FR-IT", 10, 20, 30, 40, 50, 60, 70, 80, "TRUE", "FALSE", "TRUE", "FALSE")
                    ),
                    List.of()
            ));


        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFile.xlsx");
        trajectory.setVersion(1);
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc("TestFile.xlsx"))
                .thenReturn(Optional.of(trajectory));


    linkFileProcessorService.processLinkFile(tempFile, "2032-2033", 1);


    verify(warningMessageService).getMessage(
            WarningCode.LINKS_ALL_VALUES_ZERO.value(), "2", "1", "TestFile.xlsx"
    );
    verify(warningMessageService).getMessage(
            WarningCode.LINKS_UNILATERAL_VALUES_ZERO.value(), "2", "1", "TestFile.xlsx"
    );
    verify(warningMessageService).getMessage(
            WarningCode.AREAS_NOT_ORDERED_ALPHABETICALLY.value(), "FR-CH", "2", "1", "TestFile.xlsx"
    );

}

    @Test
    void validateLinkAreas_validLink() {
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        List<String> areaNames = new ArrayList<>(List.of("FR", "CH", "IT"));
        String link = "FR-CH";
        String result = linkFileProcessorService.validateLinkAreas(link, areaNames);
        assertEquals(link, result);
    }

    @Test
    void validateLinkAreas_invalidLinkFormat() {
        List<String> areaNames = List.of("FR", "CH", "IT");
        String link = "FRCH";
        Exception exception = assertThrows(TechnicalAntaresDataMangerException.class, () -> {
            linkFileProcessorService.validateLinkAreas(link, areaNames);
        });
        assertEquals("Error: Link FRCH in LINKS file is not valid", exception.getMessage());
    }

    @Test
    void validateLinkAreas_areaNotInTrajectory() {
        List<String> areaNames = List.of("FR", "CH", "IT");
        String link = "FR-ES";
        Exception exception = assertThrows(TechnicalAntaresDataMangerException.class, () -> {
            linkFileProcessorService.validateLinkAreas(link, areaNames);
        });
        assertEquals("Error: Area ES in LINKS file is not present in AREA trajectory", exception.getMessage());
    }

    private static byte[] generateTestExcelFile() throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2030-2031");
            sheet.createRow(1).createCell(1).setCellValue(100.5);
            workbook.createSheet("parameters");

            Object[][] mockValues = {
                    {"FR-CH", 200.0, 150.0, 120.0, 100.0, 80.0, 60.0, 50.0, 30.0, "true", "false", "true", "false"}
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < LinksColumns.values().length; i++) {
                headerRow.createCell(i).setCellValue(LinksColumns.values()[i].getDisplayName());
            }
            for (int i = 0; i < mockValues.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < mockValues[i].length; j++) {
                    if (mockValues[i][j] instanceof Number) {
                        row.createCell(j).setCellValue(((Number) mockValues[i][j]).doubleValue());
                    } else {
                        row.createCell(j).setCellValue(mockValues[i][j].toString());
                    }
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}