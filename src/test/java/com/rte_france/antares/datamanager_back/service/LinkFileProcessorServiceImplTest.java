package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.LinkRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
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
    private WarningRepository warningRepository;

    @Mock
    private WarningService warningService;

    @Mock
    private UserService userService;

    @Mock
    private StudyRepository studyRepository;

    @InjectMocks
    private LinkFileProcessorServiceImpl linkFileProcessorService;

    private Path tempFile;

    private TrajectoryEntity trajectoryEntity;

    @TempDir
    Path tempDir;

    @BeforeEach
     void setup(@TempDir Path tempDir) throws IOException {
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
                                .area(AreaEntity.builder().name("IT").build()).build(),
                        AreaConfigEntity.builder()
                                .area(AreaEntity.builder().name("GE").build()).build()))

                .build();
        when(trajectoryRepository.findByTypeAndStudyId(any(), any())).thenReturn(List.of(trajectoryEntity));

        when(warningService.getMessage(anyString(), any())).thenReturn("Expected message");
    }

    @Test
    void processLinkFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString())).thenReturn(Optional.of(trajectoryEntity));

        linkFileProcessorService.processLinkFile(tempFile, "2030-2031", 1);

        verify(trajectoryRepository, times(1)).save(any());
    }



    @Test
    void processLinkFile_whenTrajectoryDoesNotExist() throws IOException {
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));

        linkFileProcessorService.processLinkFile(tempFile, "2030-2031", 1);

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void testProcessLinkFileWithAllZeroWarning() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(
                tempDir,
                "TestFile.xlsx",
                List.of("parameters", "2032-2033"),
                List.of(
                        List.of("", "2032-2033"),
                        List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                                "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                                "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                                "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                                "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5
                        )),
                        List.of(
                                List.of("CH-IT", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", "FALSE", "TRUE", "FALSE"),
                                List.of("CH-FR", 10, 20, 30, 40, 50, 60, 70, 80, "TRUE", "FALSE", "TRUE", "FALSE"),
                                List.of("FR-GE", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", "FALSE", "TRUE", "FALSE")
                        )
                ));


        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFile.xlsx");
        trajectory.setVersion(1);
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFile.xlsx", "2032-2033", TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));


        linkFileProcessorService.processLinkFile(tempFile, "2032-2033", 1);

        verify(warningService).getMessage(
                WarningCode.LINKS_ALL_VALUES_ZERO.value(), "CH-IT, FR-GE"
        );

    }

    @Test
    void testProcessLinkFileWithUnilateralWarnings() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(
                tempDir,
                "TestFile.xlsx",
                List.of("parameters", "2032-2033"),
                List.of(
                        List.of("", "2032-2033"),
                        List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                                "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                                "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                                "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                                "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5
                        )),
                        List.of(
                                List.of("CH-IT", 0, 15, 0, 20, 0, 30, 0, 50, "TRUE", "FALSE", "TRUE", "FALSE"),
                                List.of("CH-FR", 10, 0, 30, 0, 50, 0, 70, 0, "TRUE", "FALSE", "TRUE", "FALSE"),
                                List.of("FR-GE", 10, 20, 20, 50, 30, 80, 400, 100, "TRUE", "FALSE", "TRUE", "FALSE")
                        )
                ));


        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFile.xlsx");
        trajectory.setVersion(1);
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFile.xlsx", "2032-2033", TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));


        linkFileProcessorService.processLinkFile(tempFile, "2032-2033", 1);

        verify(warningService).getMessage(
                WarningCode.LINKS_UNILATERAL_VALUES_ZERO.value(), "CH-IT, CH-FR"
        );
    }

    @Test
    void testProcessLinkFileAlphebaticallyOrderedException() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(
                tempDir,
                "TestFile.xlsx",
                List.of("parameters", "2032-2033"),
                List.of(
                        List.of("", "2032-2033"),
                        List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                                "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                                "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                                "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                                "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5
                        )),
                        List.of(
                                List.of("CH-IT", 10, 15, 10, 20, 10, 30, 10, 50, "TRUE", "FALSE", "TRUE", "FALSE"),
                                List.of("FR-CH", 10, 50, 30, 530, 50, 20, 70, 30, "TRUE", "FALSE", "TRUE", "FALSE"),
                                List.of("GE-FR", 10, 20, 20, 50, 30, 80, 400, 100, "TRUE", "FALSE", "TRUE", "FALSE")
                        )
                ));


        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFile.xlsx");
        trajectory.setVersion(1);
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFile.xlsx", "2032-2033",TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));


        var exception = assertThrows(BusinessException.class, () -> {
            linkFileProcessorService.processLinkFile(tempFile, "2032-2033", 1);
        });

        assertTrue(exception.getMessage().contains("cannot be imported"));
        assertTrue(exception.getErrorMessageArguments().get(0).contains("Links FR-CH, GE-FR must be arranged in alphabetical order."));
    }

    @Test
    void validateLinkAreas_validLink() {
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        List<String> areaNames = new ArrayList<>(List.of("FR", "CH", "IT"));
        String link = "CH-FR";
        String result = linkFileProcessorService.validateLinkAreas(link, areaNames);
        assertEquals(link, result);
    }

    @Test
    void validateLinkAreas_invalidLinkFormat() {
        List<String> areaNames = List.of("FR", "CH", "IT");
        String link = "FRCH";
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            linkFileProcessorService.validateLinkAreas(link, areaNames);
        });
        assertEquals("Error: Link {0} in LINKS file is not valid", exception.getMessage());
        assertEquals(Collections.singletonList("FRCH"), exception.getErrorMessageArguments());
    }

    @Test
    void validateLinkAreas_areaNotInTrajectory() {
        List<String> areaNames = List.of("FR", "CH", "IT", "ZE", "OT");
        String link = "FR-ES";
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            linkFileProcessorService.validateLinkAreas(link, areaNames);
        });
        assertEquals("Areas {0} in LINKS file is not present in AREA trajectory", exception.getMessage());
        assertEquals(List.of("ES"), exception.getErrorMessageArguments());

    }

    @Test
    void validateLinkAreas_caseInsensitive() {
        List<String> areaNames = List.of("FR", "CH", "ITcn");
        String link = "fr-itCN";

        assertDoesNotThrow(() -> linkFileProcessorService.validateLinkAreas(link, areaNames));
    }

    @Test
    void findListLink_whenStudyIdExists() {
        Integer studyId = 1;
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setLinkEntities(List.of(new LinkEntity(), new LinkEntity()));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), studyId))
                .thenReturn(List.of(trajectory));

        List<LinkEntity> result = linkFileProcessorService.findListLink(studyId);

        assertEquals(2, result.size());
        verify(trajectoryRepository, times(1)).findByTypeAndStudyId(TrajectoryType.LINK.name(), studyId);
    }

    @Test
    void findListLink_whenStudyIdDoesNotExist() {
        Integer studyId = 1;
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), studyId))
                .thenReturn(Collections.emptyList());

        List<LinkEntity> result = linkFileProcessorService.findListLink(studyId);

        assertTrue(result.isEmpty());
        verify(trajectoryRepository, times(1)).findByTypeAndStudyId(TrajectoryType.LINK.name(), studyId);
    }

    private static byte[] generateTestExcelFile() throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("2030-2031");
            sheet.createRow(1).createCell(1).setCellValue(100.5);
            var parametersSheet = workbook.createSheet("parameters");
            parametersSheet.createRow(0).createCell(1).setCellValue("2030-2031");
            parametersSheet.createRow(1).createCell(0).setCellValue("Hurdle Costs");
            parametersSheet.createRow(1).createCell(1).setCellValue(0.5);

            Object[][] mockValues = {
                    {"CH-FR", 200.0, 150.0, 120.0, 100.0, 80.0, 60.0, 50.0, 30.0, "true", "false", "true", "false"}
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


    @Test
    void testAccumulatedWarningsForAllZeros() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(
                tempDir,
                "TestFileWar.xlsx",
                List.of("parameters", "2033-2034"),
                List.of(
                        List.of("", "2033-2034"),
                        List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                                "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                                "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                                "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                                "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5
                        )),
                        List.of(
                                List.of("CH-FR", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", "FALSE", "TRUE", "FALSE"),
                                List.of("FR-IT", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", "FALSE", "TRUE", "FALSE")
                        )
                ));

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFileWar.xlsx");
        trajectory.setVersion(1);
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFileWar.xlsx", "2033-2034", TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));

        linkFileProcessorService.processLinkFile(tempFile, "2033-2034", 1);

        verify(warningService, times(1)).getMessage(
                WarningCode.LINKS_ALL_VALUES_ZERO.value(),
                "CH-FR, FR-IT"

        );
    }

    @Test
    void testAccumulatedWarningsForAreaNotPresent() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(
                tempDir,
                "TestFileWar.xlsx",
                List.of("parameters", "2033-2034"),
                List.of(
                        List.of("", "2033-2034"),
                        List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                                "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                                "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                                "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                                "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5
                        )),
                        List.of(
                                List.of("FR-IT", 20, 50, 50, 30, 40, 60, 80, 90, "TRUE", "FALSE", "TRUE", "FALSE")
                        )
                ));

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFileWar.xlsx");
        trajectory.setVersion(1);

        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFileWar.xlsx", "2033-2034", TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));

        linkFileProcessorService.processLinkFile(tempFile, "2033-2034", 1);

        verify(warningService, times(1)).getMessage(
                WarningCode.LINKS_AREA_NOT_PRESENT.value(),
                "CH, GE"
        );


    }

    @Test
    void testWarningForUnilateralValuesZero() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(
                tempDir,
                "TestFileWar.xlsx",
                List.of("parameters", "2033-2034"),
                List.of(
                        List.of("", "2033-2034"),
                        List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                                "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                                "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                                "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                                "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5)),
                        List.of(
                                List.of("CH-FR", 0, 50, 0, 30, 0, 10, 0, 90, "TRUE", "FALSE", "TRUE", "FALSE"),
                                List.of("FR-IT", 10, 0, 30, 0, 50, 0, 80, 0, "TRUE", "FALSE", "TRUE", "FALSE"),
                                List.of("FR-GE", 10, 0, 30, 0, 50, 0, 80, 0, "TRUE", "FALSE", "TRUE", "FALSE")
                        )
                ));

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFileWar.xlsx");
        trajectory.setVersion(1);
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFileWar.xlsx", "2033-2034", TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));

        linkFileProcessorService.processLinkFile(tempFile, "2033-2034", 1);

        verify(warningService).getMessage(
                eq(WarningCode.LINKS_UNILATERAL_VALUES_ZERO.value()),
                eq("CH-FR, FR-IT, FR-GE")
        );
    }

}