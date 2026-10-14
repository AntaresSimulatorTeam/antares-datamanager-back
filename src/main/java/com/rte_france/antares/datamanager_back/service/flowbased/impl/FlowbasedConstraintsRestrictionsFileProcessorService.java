package com.rte_france.antares.datamanager_back.service.flowbased.impl;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedConstraintsRestrictionEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkListEntity;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.FlowbasedConstraintsRestrictionsValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EmptyFileException;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowbasedConstraintsRestrictionsFileProcessorService {

    public static final String DIRECTORY = "restriction_ahc";
    public static final String FILE_NAME = "contrainte_restriction_ahc.xlsx";

    private final PathSecurityUtil pathSecurityUtil;

    public RestrictionsData readRestrictions(Path trajectoryDirectory, String horizon) {
        Path file = pathSecurityUtil.resolveSafePath(trajectoryDirectory, DIRECTORY, FILE_NAME);
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            return new RestrictionsData(List.of(), List.of());
        }

        try (InputStream input = Files.newInputStream(file);
             Workbook workbook = WorkbookFactory.create(input)) {
            var parsed = FlowbasedConstraintsRestrictionsValidator.parse(workbook, horizon, FILE_NAME);
            List<FlowbasedConstraintsRestrictionEntity> constraints = parsed.constraints().stream()
                    .map(row -> FlowbasedConstraintsRestrictionEntity.builder()
                            .name(row.name())
                            .enabled(row.enabled())
                            .sign(row.sign())
                            .temporality(row.temporality())
                            .type(row.type())
                            .listLinks(row.listLinks())
                            .secondMember(row.secondMember())
                            .build())
                    .toList();

            List<FlowbasedLinkListEntity> links = new ArrayList<>();
            parsed.linkLists().forEach((listName, values) -> {
                for (int position = 0; position < values.size(); position++) {
                    links.add(FlowbasedLinkListEntity.builder()
                            .listName(listName)
                            .link(values.get(position))
                            .position(position)
                            .build());
                }
            });
            return new RestrictionsData(constraints, links);
        } catch (IOException | EmptyFileException | UnsupportedFileFormatException | POIXMLException e) {
            log.error("Error reading Flowbased restrictions workbook {}", file, e);
            throw BusinessException.builder()
                    .message("Error reading Flowbased restrictions file {0}: {1}")
                    .errorMessageArguments(List.of(FILE_NAME, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    public record RestrictionsData(List<FlowbasedConstraintsRestrictionEntity> constraints,
                                   List<FlowbasedLinkListEntity> links) {
    }
}
