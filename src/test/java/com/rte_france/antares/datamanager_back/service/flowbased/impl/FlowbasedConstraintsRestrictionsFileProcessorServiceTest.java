package com.rte_france.antares.datamanager_back.service.flowbased.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedConstraintsRestrictionEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkListEntity;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FlowbasedConstraintsRestrictionsFileProcessorServiceTest {

    @TempDir
    Path tempDir;

    private final FlowbasedConstraintsRestrictionsFileProcessorService processor =
            new FlowbasedConstraintsRestrictionsFileProcessorService(
                    new PathSecurityUtil(mock(AntaresDataManagerProperties.class)));

    @Test
    void absentWorkbookLeavesRestrictionsEmpty() {
        var result = processor.readRestrictions(tempDir, "2027");

        assertThat(result.constraints()).isEmpty();
        assertThat(result.links()).isEmpty();
    }

    @Test
    void absentWorkbookReturnsModifiableCollections() {
        var result = processor.readRestrictions(tempDir, "2027");

        result.constraints().clear();
        result.links().clear();
        result.constraints().add(new FlowbasedConstraintsRestrictionEntity());
        result.links().add(new FlowbasedLinkListEntity());

        assertThat(result.constraints()).hasSize(1);
        assertThat(result.links()).hasSize(1);
    }

    @Test
    void parsedWorkbookReturnsModifiableCollections() throws IOException {
        createWorkbook("2027");
        var result = processor.readRestrictions(tempDir, "2026-2027");
        var constraints = new ArrayList<>(result.constraints());
        var links = new ArrayList<>(result.links());

        result.constraints().clear();
        result.links().clear();
        result.constraints().addAll(constraints);
        result.links().addAll(links);

        assertThat(result.constraints()).containsExactlyElementsOf(constraints);
        assertThat(result.links()).containsExactlyElementsOf(links);
    }

    @Test
    void mapsConstraintsAndSharesReferencedLists() throws IOException {
        createWorkbook("2027");

        var result = processor.readRestrictions(tempDir, "2026-2027");

        assertThat(result.constraints()).hasSize(2);
        var constraint = result.constraints().getFirst();
        assertThat(constraint.getName()).isEqualTo("restriction_ahc");
        assertThat(constraint.getEnabled()).isTrue();
        assertThat(constraint.getSign()).isEqualTo("less");
        assertThat(constraint.getTemporality()).isEqualTo("hourly");
        assertThat(constraint.getType()).isEqualTo("flowbased");
        assertThat(constraint.getListLinks()).isEqualTo("listLinks1");
        assertThat(constraint.getSecondMember()).isEqualTo("ts10GW");
        assertThat(result.constraints().get(1).getEnabled()).isFalse();
        assertThat(result.links()).extracting(FlowbasedLinkListEntity::getLink)
                .containsExactlyInAnyOrder("ch-fr", "fr-itn", "fr-zz_flowbased");
        assertThat(result.links()).extracting(FlowbasedLinkListEntity::getListName)
                .containsOnly("listLinks1");
    }

    @Test
    void rejectsWrongHorizonInsteadOfUsingAnotherSheet() throws IOException {
        createWorkbook("2021");

        var exception = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> processor.readRestrictions(tempDir, "2027"));
        assertThat(exception.getErrorMessageArguments()).contains("2027");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsUnreadableWorkbookRatherThanSkippingIt() throws IOException {
        Path file = restrictionsFile();
        Files.writeString(file, "not an Excel workbook");

        assertThatThrownBy(() -> processor.readRestrictions(tempDir, "2027"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getHttpStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsWorkbookSymlinkOutsideTrajectory() throws IOException {
        Path base = Files.createDirectory(tempDir.resolve("trajectory"));
        Path restrictions = Files.createDirectory(base.resolve("restriction_ahc"));
        Path outside = Files.writeString(tempDir.resolve("outside.xlsx"), "outside");
        Files.createSymbolicLink(restrictions.resolve("contrainte_restriction_ahc.xlsx"), outside);

        assertThatThrownBy(() -> processor.readRestrictions(base, "2027"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside of the allowed directory");
    }

    @Test
    void rejectsEscapingDirectoryEvenWhenWorkbookIsAbsent() throws IOException {
        Path base = Files.createDirectory(tempDir.resolve("trajectory"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.createSymbolicLink(base.resolve("restriction_ahc"), outside);

        assertThatThrownBy(() -> processor.readRestrictions(base, "2027"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside of the allowed directory");
    }

    private Path restrictionsFile() throws IOException {
        Path directory = Files.createDirectories(tempDir.resolve("restriction_ahc"));
        return directory.resolve("contrainte_restriction_ahc.xlsx");
    }

    private void createWorkbook(String horizonSheet) throws IOException {
        try (var workbook = new XSSFWorkbook()) {
            var lists = workbook.createSheet("listLinks");
            var listHeader = lists.createRow(0);
            listHeader.createCell(0).setCellValue("listLinks1");
            listHeader.createCell(2).setCellValue("unused");
            lists.createRow(1).createCell(0).setCellValue("ch-fr");
            lists.createRow(2).createCell(0).setCellValue("fr-itn");
            lists.createRow(3).createCell(0).setCellValue("fr-zz_flowbased");
            lists.getRow(1).createCell(2).setCellValue("fr-es");
            var sheet = workbook.createSheet(horizonSheet);
            String[] headers = {"nom", "enabled", "signe", "temporalit\u00e9", "type",
                    "information forme de la contrainte", "listLinks", "second_member"};
            var header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }
            for (int index = 1; index <= 2; index++) {
                var row = sheet.createRow(index);
                String[] values = {index == 1 ? "restriction_ahc" : "restriction_ahc_2",
                        index == 1 ? "YES" : "no", "less", "hourly", "flowbased",
                        "Informative text is not stored", "listLinks1", "ts10GW"};
                for (int column = 0; column < values.length; column++) {
                    row.createCell(column).setCellValue(values[column]);
                }
            }
            try (var output = Files.newOutputStream(restrictionsFile())) {
                workbook.write(output);
            }
        }
    }
}
