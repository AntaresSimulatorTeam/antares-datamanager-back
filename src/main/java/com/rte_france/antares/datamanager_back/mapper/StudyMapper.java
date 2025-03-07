package com.rte_france.antares.datamanager_back.mapper;


import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.data.domain.Page;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StudyMapper {

    public static StudyDTO toStudyDTO(StudyEntity entity) {
        Objects.requireNonNull(entity);

        var latestTrajectories = extractKeyFromColumnByComparator(
                entity,
                TrajectoryEntity::getFileName,
                Comparator.comparing(TrajectoryEntity::getCreationDate),
                StudyEntity::getTrajectories
        );
        var sortedTrajectoryIds = sortedByComparator(latestTrajectories.values(), Comparator.comparing(TrajectoryEntity::getCreationDate));
        return StudyDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .createdBy(entity.getCreatedBy())
                .creationDate(entity.getCreationDate())
                .project(entity.getProject().getName())
                .tags(entity.getTags())
                .horizon(entity.getHorizon())
                .status(entity.getStatus().name())
                .trajectoryIds(sortedTrajectoryIds)
                .build();
    }

    private static List<Integer> sortedByComparator(Collection<TrajectoryEntity> latestTrajectories, Comparator<TrajectoryEntity> comparator) {
        return latestTrajectories.stream()
                .sorted(comparator)
                .map(TrajectoryEntity::getId)
                .collect(Collectors.toList());
    }

    private static <T, U> Map<T, U> extractKeyFromColumnByComparator(StudyEntity entity, Function<U, T> keyExtractor, Comparator<U> comparator, Function<StudyEntity, Set<U>> columnExtractor) {
        return columnExtractor.apply(entity).stream()
                .collect(Collectors.toMap(
                        keyExtractor,
                        Function.identity(),
                        BinaryOperator.maxBy(comparator)
                ));
    }

    private static Map<String, TrajectoryEntity> extractKeyFromColumnByComparator(StudyEntity entity) {
      return entity.getTrajectories().stream()
              .collect(Collectors.toMap(
                          TrajectoryEntity::getFileName,
                          Function.identity(),
                          BinaryOperator.maxBy(Comparator.comparing(TrajectoryEntity::getCreationDate))
                      )
              );
    }

    public static Page<StudyDTO> toStudyPage(Page<StudyEntity> page) {
        return page.map(StudyMapper::toStudyDTO);
    }
}
