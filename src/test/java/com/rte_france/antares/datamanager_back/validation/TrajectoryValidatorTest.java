package com.rte_france.antares.datamanager_back.validation;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TrajectoryValidatorTest {

    private TrajectoryValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TrajectoryValidator();
    }

    @Test
    void testValidTrajectoryName() {
        assertDoesNotThrow(() -> validator.validate("my_trajectory", 60));
    }

    @Test
    void testValidTrajectoryNameWithSpaces() {
        assertDoesNotThrow(() -> validator.validate("my trajectory", 60));
    }

    @Test
    void testValidTrajectoryNameWithMultipleSpaces() {
        assertDoesNotThrow(() -> validator.validate("my trajectory with spaces", 60));
    }

    @Test
    void testInvalidTrajectoryNameOnlySpaces() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validate("   ", 60));
        assertTrue(exception.getMessage().contains("cannot contain only spaces"));
    }

    @Test
    void testInvalidTrajectoryNameExceedsMaxLength() {
        String longName = "a".repeat(61);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validate(longName, 60));
        assertTrue(exception.getMessage().contains("cannot exceed"));
    }

    @Test
    void testValidTrajectoryNameMaxLength() {
        String maxName = "a".repeat(60);
        assertDoesNotThrow(() -> validator.validate(maxName, 60));
    }

    @Test
    void testValidNullTrajectoryName() {
        assertDoesNotThrow(() -> validator.validate(null, 60));
    }

    @Test
    void testValidTrajectoryNameWithLeadingAndTrailingSpaces() {
        assertDoesNotThrow(() -> validator.validate("  my_trajectory  ", 60));
    }

    @Test
    void testValidateWithCustomMaxLength() {
        String maxName = "a".repeat(30);
        assertDoesNotThrow(() -> validator.validate(maxName, 30));
    }

    @Test
    void testValidateWithCustomMaxLengthExceeded() {
        String longName = "a".repeat(31);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validate(longName, 30));
        assertTrue(exception.getMessage().contains("cannot exceed"));
        assertTrue(exception.getMessage().contains("30"));
    }

    static Stream<String> trajectoryPrefixes() {
        return Stream.of("areas_", "cluster_dsr_", "CM_", "links_", "installedMisc_", "installedRES_",
                        "repartition_techno_", "repartition_zonale_", "general_data_", "scenario_builder_",
                        "talon_nuc_", "talon_epr_", "talon_smr_", "cluster_battery_", "cluster_NewTech42_",
                        "thermal_", "common_param_", "specific_param_", "costs_", "economic_param_")
                .flatMap(prefix -> Stream.of(prefix, prefix.toLowerCase(Locale.ROOT), prefix.toUpperCase(Locale.ROOT)))
                .distinct();
    }

    @ParameterizedTest
    @MethodSource("trajectoryPrefixes")
    void excludesOneLeadingPrefixFromLength(String prefix) {
        assertDoesNotThrow(() -> validator.validate(prefix + "a".repeat(60), 60));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validate(prefix + "a".repeat(61), 60));
        assertTrue(exception.getMessage().contains("cannot exceed 60"));
    }

    @ParameterizedTest
    @MethodSource("trajectoryPrefixes")
    void rejectsBlankNameAfterPrefix(String prefix) {
        assertThrows(BusinessException.class, () -> validator.validate(prefix, 60));
        assertThrows(BusinessException.class, () -> validator.validate(prefix + "   ", 60));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "unknown_", "before_areas_", "cluster__", "cluster_", " areas_", "ts_epr_", "ts_smr_"})
    void countsUnknownMalformedAndNonLeadingPrefixes(String prefix) {
        assertDoesNotThrow(() -> validator.validate(prefix + "a".repeat(60 - prefix.length()), 60));
        assertThrows(BusinessException.class,
                () -> validator.validate(prefix + "a".repeat(61 - prefix.length()), 60));
    }

    @Test
    void removesOnlyOnePrefix() {
        assertDoesNotThrow(() -> validator.validate("areas_links_" + "a".repeat(54), 60));
        assertThrows(BusinessException.class,
                () -> validator.validate("areas_links_" + "a".repeat(55), 60));
    }

    @Test
    void dynamicTechnologyEndsAtFirstUnderscore() {
        assertDoesNotThrow(() -> validator.validate("cluster_battery_" + "my_name_".repeat(7) + "abcd", 60));
        assertThrows(BusinessException.class,
                () -> validator.validate("cluster_li_ion_" + "a".repeat(60), 60));
    }

    @Test
    void countsFileExtensionInRemainingName() {
        assertDoesNotThrow(() -> validator.validate("areas_" + "a".repeat(55) + ".xlsx", 60));
        assertThrows(BusinessException.class,
                () -> validator.validate("areas_" + "a".repeat(56) + ".xlsx", 60));
    }

    @Test
    void appliesExplicitLimitsAfterPrefix() {
        assertDoesNotThrow(() -> validator.validate("areas_" + "a".repeat(30), 30));
        assertThrows(BusinessException.class, () -> validator.validate("areas_" + "a".repeat(31), 30));
        assertDoesNotThrow(() -> validator.validate("areas_" + "a".repeat(60), 60));
        assertThrows(BusinessException.class, () -> validator.validate("areas_" + "a".repeat(61), 60));
    }

    @Test
    void prefixLengthDoesNotAddAnotherInputLimit() {
        String prefix = "cluster_" + "t".repeat(250) + "_";
        assertDoesNotThrow(() -> validator.validate(prefix + "a".repeat(60), 60));
        assertThrows(BusinessException.class, () -> validator.validate(prefix + "a".repeat(61), 60));
    }
}
