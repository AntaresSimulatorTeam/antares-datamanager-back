package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.LinkRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.area_link.impl.LinkFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.common.WarningService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        StudyEntity studyEntity = StudyEntity.builder().id(1).build();
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(studyRepository.findById(any())).thenReturn(Optional.of(studyEntity));
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString())).thenReturn(Optional.of(trajectoryEntity));

        linkFileProcessorService.processLinkFile(tempFile, "2030-2031", 1, true);

        verify(trajectoryRepository, times(1)).save(any());
        verify(studyRepository, atLeastOnce()).save(studyEntity);
        assertTrue(studyEntity.getHvdc());
    }



    @Test
    void processLinkFile_whenTrajectoryDoesNotExist() throws IOException {
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));

        linkFileProcessorService.processLinkFile(tempFile, "2030-2031", 1,false);

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
                                "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5), List.of("HVDC", false, false)),
                List.of(
                        List.of("CH-IT", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", 0, 0, 0, 0, 0, 0),
                        List.of("CH-FR", 10, 20, 30, 40, 50, 60, 70, 80, "TRUE", 10, 10, 1, 1, 1, 1),
                        List.of("FR-GE", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", 0, 0, 0, 0, 0, 0)
                )
                ));


        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFile.xlsx");
        trajectory.setVersion(1);
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFile.xlsx", "2032-2033", TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));


        linkFileProcessorService.processLinkFile(tempFile, "2032-2033", 1,false);

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
                                "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5), List.of("HVDC", false, false)),
                List.of(
                        List.of("CH-IT", 0, 15, 0, 20, 0, 30, 0, 50, "TRUE", 0, 10, 0, 1, 0, 1),
                        List.of("CH-FR", 10, 0, 30, 0, 50, 0, 70, 0, "TRUE", 10, 0, 1, 0, 1, 0),
                        List.of("FR-GE", 10, 20, 20, 50, 30, 80, 400, 100, "TRUE", 10, 10, 1, 1, 1, 1)
                )
                ));


        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFile.xlsx");
        trajectory.setVersion(1);
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFile.xlsx", "2032-2033", TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));


        linkFileProcessorService.processLinkFile(tempFile, "2032-2033", 1,false);

        verify(warningService).getMessage(
                WarningCode.LINKS_UNILATERAL_VALUES_ZERO.value(), "CH-IT, CH-FR"
        );
    }

    @Test
    void processLinkFile_whenHvdcColumnsAreEmpty() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(
                tempDir,
                "EmptyHvdc.xlsx",
                List.of("parameters", "2030-2031"),
                List.of(
                        List.of("Parameter", "2030-2031"),
                        List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                                "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                                "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                                "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                                "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                                "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect")
                ),
                List.of(
                        List.of(List.of("hurdle_cost", 10.5), List.of("HVDC", true)),
                        List.of(
                                List.of("AT-CH", 100, 200, 150, 175, 300, 400, 250, 275, "TRUE", "", "", "", "", "", ""),
                                List.of("AT-CZ", 110, 210, 160, 185, 310, 410, 260, 285, "TRUE", 50.0, 25.0, 1, 1, 1, 1)
                        )
                ));

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        TrajectoryEntity areaTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA.name())
                .areaConfigEntities(List.of(
                        AreaConfigEntity.builder().area(AreaEntity.builder().name("AT").build()).build(),
                        AreaConfigEntity.builder().area(AreaEntity.builder().name("CH").build()).build(),
                        AreaConfigEntity.builder().area(AreaEntity.builder().name("CZ").build()).build()
                ))
                .build();
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), 1)).thenReturn(List.of(areaTrajectory));
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> linkFileProcessorService.processLinkFile(tempFile, "2030-2031", 1,false));

        verify(trajectoryRepository, times(1)).save(any());
        verify(linkRepository, times(1)).saveAll(argThat(links -> {
            LinkEntity atCh = ((List<LinkEntity>)links).stream().filter(l -> "AT-CH".equals(l.getName())).findFirst().orElseThrow();
            return atCh.getHvdcMwDirect() == null && atCh.getHvdcMwIndirect() == null;
        }));
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
                                "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5), List.of("HVDC", false, false)),
                List.of(
                        List.of("CH-IT", 10, 15, 10, 20, 10, 30, 10, 50, "TRUE", 10, 0, 1, 1, 1, 1),
                        List.of("FR-CH", 10, 50, 30, 530, 50, 20, 70, 30, "TRUE", 0, 10, 1, 1, 1, 1),
                        List.of("GE-FR", 10, 20, 20, 50, 30, 80, 400, 100, "TRUE", 10, 10, 1, 1, 1, 1)
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
            linkFileProcessorService.processLinkFile(tempFile, "2032-2033", 1,false);
        });

        System.out.println(exception.getMessage());
        assertTrue(exception.getMessage().contains("Links {1} must be arranged in alphabetical order."));
        assertEquals(exception.getErrorMessageArguments().get(1), "FR-CH, GE-FR");
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
            parametersSheet.createRow(2).createCell(0).setCellValue("HVDC");
            parametersSheet.createRow(2).createCell(1).setCellValue(false);
            Object[][] mockValues = {
                    {"CH-FR", 200.0, 150.0, 120.0, 100.0, 80.0, 60.0, 50.0, 30.0, "true", 75.0, 25.0, 2, 1, 1, 1}
            };

            Row headerRow = sheet.createRow(0);
            String[] headers = {"Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                    "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                    "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                    "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                    "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
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
                                "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5), List.of("HVDC", false, false)),
                        List.of(
                                List.of("CH-FR", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", 0, 0, 0, 0, 0, 0),
                                List.of("FR-IT", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", 0, 0, 0, 0, 0, 0)
                        )
                ));

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFileWar.xlsx");
        trajectory.setVersion(1);
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFileWar.xlsx", "2033-2034", TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));

        linkFileProcessorService.processLinkFile(tempFile, "2033-2034", 1,false);

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
                                "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5), List.of("HVDC", false, false)),
                        List.of(
                                List.of("FR-IT", 20, 50, 50, 30, 40, 60, 80, 90, "TRUE", 10, 10, 1, 1, 1, 1)
                        )
                ));

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFileWar.xlsx");
        trajectory.setVersion(1);

        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFileWar.xlsx", "2033-2034", TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));

        linkFileProcessorService.processLinkFile(tempFile, "2033-2034", 1,false);

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
                                "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect")
                ),
                List.of(
                        List.of(List.of("Hurdle Costs", 0, 5), List.of("HVDC", false, false)),
                        List.of(
                                List.of("CH-FR", 0, 50, 0, 30, 0, 10, 0, 90, "TRUE", 0, 10, 0, 1, 0, 1),
                                List.of("FR-IT", 10, 0, 30, 0, 50, 0, 80, 0, "TRUE", 10, 0, 1, 0, 1, 0),
                                List.of("FR-GE", 10, 0, 30, 0, 50, 0, 80, 0, "TRUE", 10, 0, 1, 0, 1, 0)
                        )
                ));

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("TestFileWar.xlsx");
        trajectory.setVersion(1);
        when(studyRepository.findById(any())).thenReturn(Optional.of(StudyEntity.builder().build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("TestFileWar.xlsx", "2033-2034", TrajectoryType.LINK.name()))
                .thenReturn(Optional.of(trajectory));

        linkFileProcessorService.processLinkFile(tempFile, "2033-2034", 1,false);

        verify(warningService).getMessage(
                eq(WarningCode.LINKS_UNILATERAL_VALUES_ZERO.value()),
                eq("CH-FR, FR-IT, FR-GE")
        );
    }

    @Test
    void testCheckConsistencyTrajectoryLinkAndArea_WithException_StopsExecution() {
        // Given
        List<LinkEntity> linkEntities = List.of(
                LinkEntity.builder().name("FR-DE").build()
        );
        List<String> areaNames = Arrays.asList("FR", "ES");
        Set<WarningMessageEntity> warningMessages = new HashSet<>();
        Integer studyId = 1;
        Integer trajectoryId = 2;
        String userNni = "testUser";
        TrajectoryEntity secondTrajectory = TrajectoryEntity.builder().id(3).build();
        StudyEntity study = StudyEntity.builder().id(studyId).build();

        // When
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));

        // Spy to verify methods calls
        LinkFileProcessorServiceImpl serviceSpy = spy(linkFileProcessorService);
        doThrow(BusinessException.builder()
                .message("Areas {0} in LINKS file is not present in AREA trajectory")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .errorMessageArguments(List.of("ES"))
                .build())
                .when(serviceSpy)
                .validateLinkAreas(anyString(), anyList());


        assertThrows(BusinessException.class, () ->
                serviceSpy.checkConsistencyTrajectoryLinkAndArea(
                        linkEntities, areaNames, warningMessages,
                        studyId, trajectoryId, secondTrajectory, userNni)
        );


        verify(studyRepository).findById(studyId);

        verify(serviceSpy).validateLinkAreas(anyString(), anyList());


        verify(warningService, never()).getMessage(anyString(), any());
        verify(warningRepository, never()).existsByWarningContentAndTrajectoryIdAndStudyId(anyString(), anyInt(), anyInt());

        assertTrue(warningMessages.isEmpty());
    }

    @Test
    void testCheckIfHorizonExist_IOException_ThrowsTechnicalException() throws IOException {
        Path invalidPath = Path.of("non_existent_file_path_67890");

        TechnicalException exception = assertThrows(TechnicalException.class, () -> {
            linkFileProcessorService.processLinkFile(invalidPath, "2030-2031", 1,false);
        });

        assertTrue(exception.getMessage().contains("could not check if horizon exist") || exception.getMessage().contains("non_existent_file_path_67890"));
    }
    @Test
    void saveTrajectory_shouldThrowExceptionWhenFileNameExceedsMaxLength() {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName("ThisFileNameIsWayTooLongToBeAcceptedByTheSystemAndShouldFail")
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () ->
                linkFileProcessorService.saveTrajectory(trajectory, List.of(), Set.of()));

        assertEquals("Trajectory name cannot exceed 40 characters.", exception.getMessage());
    }

    @Test
    void saveTrajectory_shouldNotThrowExceptionWhenFileNameIsWithinMaxLength() {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName("ValidFileNameWithinLimit")
                .build();

        assertDoesNotThrow(() ->
                linkFileProcessorService.saveTrajectory(trajectory, List.of(), Set.of()));
    }

    @Test
    void throwBusinessExceptionWhenHorizonNotFoundInHeaderRow() {
        Sheet mockSheet = mock(Sheet.class);
        Row mockRow = mock(Row.class);
        when(mockSheet.getRow(0)).thenReturn(mockRow);
        when(mockRow.iterator()).thenReturn(Collections.emptyIterator());

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            linkFileProcessorService.findCellIndexByHorizon(mockSheet, "2030-2031");
        });

        assertEquals("Horizon {0} not found in the header row.", exception.getMessage());
        assertEquals(Collections.singletonList("2030-2031"), exception.getErrorMessageArguments());
    }

    @Test
    void throwBusinessExceptionWhenHeaderRowIsMissing() {
        Sheet mockSheet = mock(Sheet.class);
        when(mockSheet.getRow(0)).thenReturn(null);

        TechnicalException exception = assertThrows(TechnicalException.class, () -> {
            linkFileProcessorService.findCellIndexByHorizon(mockSheet, "2030-2031");
        });

        assertEquals("Header row is missing in the sheet.", exception.getMessage());
    }

    @Test
    void testGetNumericCellValue_NumericCell() {
        Row mockRow = mock(Row.class);
        Cell mockCell = mock(Cell.class);
        when(mockRow.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(mockCell);
        when(mockCell.getCellType()).thenReturn(CellType.NUMERIC);
        when(mockCell.getNumericCellValue()).thenReturn(123.45);

        Double result = ReflectionTestUtils.invokeMethod(linkFileProcessorService, "getNumericCellValue", mockRow, 0);

        assertEquals(123.45, result);
    }

    @Test
    void testGetNumericCellValue_StringCell_ValidDouble() {
        Row mockRow = mock(Row.class);
        Cell mockCell = mock(Cell.class);
        when(mockRow.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(mockCell);
        when(mockCell.getCellType()).thenReturn(CellType.STRING);
        when(mockCell.getStringCellValue()).thenReturn(" 123,45 ");

        Double result = ReflectionTestUtils.invokeMethod(linkFileProcessorService, "getNumericCellValue", mockRow, 0);

        assertEquals(123.45, result);
    }

    @Test
    void testGetNumericCellValue_StringCell_Empty() {
        Row mockRow = mock(Row.class);
        Cell mockCell = mock(Cell.class);
        when(mockRow.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(mockCell);
        when(mockCell.getCellType()).thenReturn(CellType.STRING);
        when(mockCell.getStringCellValue()).thenReturn("  ");

        Double result = ReflectionTestUtils.invokeMethod(linkFileProcessorService, "getNumericCellValue", mockRow, 0);

        assertNull(result);
    }

    @Test
    void testGetNumericCellValue_NullCell() {
        Row mockRow = mock(Row.class);
        when(mockRow.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(null);

        Double result = ReflectionTestUtils.invokeMethod(linkFileProcessorService, "getNumericCellValue", mockRow, 0);

        assertNull(result);
    }

    @Test
    void testGetNumericCellValue_BooleanCell_ReturnsNull() {
        Row mockRow = mock(Row.class);
        Cell mockCell = mock(Cell.class);
        when(mockRow.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).thenReturn(mockCell);
        when(mockCell.getCellType()).thenReturn(CellType.BOOLEAN);

        Double result = ReflectionTestUtils.invokeMethod(linkFileProcessorService, "getNumericCellValue", mockRow, 0);

        assertNull(result);
    }

    @Test
    void testCheckConsistencyTrajectoryLinkAndArea_MissingAreas_AddsWarning() {
        // Given
        List<LinkEntity> linkEntities = List.of(
                LinkEntity.builder().name("FR-DE").build()
        );
        List<String> areaNames = Arrays.asList("FR", "DE", "ES"); // ES is missing from links
        Set<WarningMessageEntity> warningMessages = new HashSet<>();
        Integer studyId = 1;
        Integer trajectoryId = 2;
        String userNni = "testUser";
        TrajectoryEntity secondTrajectory = TrajectoryEntity.builder().id(3).build();
        StudyEntity study = StudyEntity.builder().id(studyId).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(warningService.getMessage(anyString(), any())).thenReturn("Warning Message");
        when(warningRepository.existsByWarningContentAndTrajectoryIdAndStudyId(anyString(), anyInt(), anyInt())).thenReturn(false);

        // When
        linkFileProcessorService.checkConsistencyTrajectoryLinkAndArea(
                linkEntities, areaNames, warningMessages,
                studyId, trajectoryId, secondTrajectory, userNni);

        // Then
        assertEquals(1, warningMessages.size());
        WarningMessageEntity warning = warningMessages.iterator().next();
        assertEquals(WarningCode.LINKS_AREA_NOT_PRESENT, warning.getWarningCode());
        assertEquals("Warning Message", warning.getWarningContent());
        assertEquals(study, warning.getStudy());
        assertEquals(secondTrajectory, warning.getSecondTrajectory());
        verify(warningService).getMessage(eq(WarningCode.LINKS_AREA_NOT_PRESENT.value()), eq("ES"));
    }

    @Test
    void testCheckConsistencyTrajectoryLinkAndArea_WarningAlreadyExists() {
        // Given
        List<LinkEntity> linkEntities = List.of(
                LinkEntity.builder().name("FR-DE").build()
        );
        List<String> areaNames = Arrays.asList("FR", "DE", "ES");
        Set<WarningMessageEntity> warningMessages = new HashSet<>();
        Integer studyId = 1;
        Integer trajectoryId = 2;
        String userNni = "testUser";
        StudyEntity study = StudyEntity.builder().id(studyId).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(warningService.getMessage(anyString(), any())).thenReturn("Warning Message");
        when(warningRepository.existsByWarningContentAndTrajectoryIdAndStudyId(anyString(), anyInt(), anyInt())).thenReturn(true);

        // When
        linkFileProcessorService.checkConsistencyTrajectoryLinkAndArea(
                linkEntities, areaNames, warningMessages,
                studyId, trajectoryId, null, userNni);

        // Then
        assertTrue(warningMessages.isEmpty());
    }
}