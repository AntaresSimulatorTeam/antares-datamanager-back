package com.rte_france.antares.datamanager_back.validation;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrajectoryValidatorTest {

    private TrajectoryValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TrajectoryValidator();
    }

    @Test
    void testValidTrajectoryName() {
        assertDoesNotThrow(() -> validator.validate("my_trajectory"));
    }

    @Test
    void testValidTrajectoryNameWithSpaces() {
        assertDoesNotThrow(() -> validator.validate("my trajectory"));
    }

    @Test
    void testValidTrajectoryNameWithMultipleSpaces() {
        assertDoesNotThrow(() -> validator.validate("my trajectory with spaces"));
    }

    @Test
    void testInvalidTrajectoryNameOnlySpaces() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validate("   "));
        assertTrue(exception.getMessage().contains("cannot contain only spaces"));
    }

    @Test
    void testInvalidTrajectoryNameExceedsMaxLength() {
        String longName = "a".repeat(41);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validate(longName));
        assertTrue(exception.getMessage().contains("cannot exceed"));
    }

    @Test
    void testValidTrajectoryNameMaxLength() {
        String maxName = "a".repeat(40);
        assertDoesNotThrow(() -> validator.validate(maxName));
    }

    @Test
    void testValidNullTrajectoryName() {
        assertDoesNotThrow(() -> validator.validate(null));
    }

    @Test
    void testValidTrajectoryNameWithLeadingAndTrailingSpaces() {
        assertDoesNotThrow(() -> validator.validate("  my_trajectory  "));
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
}
