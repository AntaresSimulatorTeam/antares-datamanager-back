package com.rte_france.antares.datamanager_back.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecimalUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "'1.5', 1.5",
            "'1,5', 1.5",
            "' 2,25 ', 2.25",
            "'-3', -3",
            "'.5', 0.5",
            "'1e3', 1000"
    })
    void parseDecimal_acceptsDotAndCommaSeparators(String input, String expected) {
        assertThat(DecimalUtils.parseDecimal(input)).isEqualByComparingTo(new BigDecimal(expected));
        assertThat(DecimalUtils.parseDouble(input)).isEqualTo(Double.parseDouble(expected));
        assertThat(DecimalUtils.isDecimal(input)).isTrue();
        assertThat(DecimalUtils.isDouble(input)).isTrue();
        assertThat(DecimalUtils.tryParseDecimal(input)).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo(new BigDecimal(expected)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "1,2,3", "1.2.3", "12%"})
    void invalidValues_areRejected(String input) {
        assertThat(DecimalUtils.isDecimal(input)).isFalse();
        assertThat(DecimalUtils.isDouble(input)).isFalse();
        assertThat(DecimalUtils.tryParseDecimal(input)).isEmpty();
        assertThatThrownBy(() -> DecimalUtils.parseDecimal(input)).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> DecimalUtils.parseDouble(input)).isInstanceOf(NumberFormatException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Infinity", "-Infinity", "NaN"})
    void nonFiniteValues_areAcceptedAsDoubleOnly(String input) {
        assertThat(DecimalUtils.isDouble(input)).isTrue();
        assertThat(DecimalUtils.parseDouble(input)).isEqualTo(Double.parseDouble(input));
        assertThat(DecimalUtils.isDecimal(input)).isFalse();
    }

    @Test
    void parseDecimal_preservesScale() {
        assertThat(DecimalUtils.parseDecimal("1,50")).isEqualTo(new BigDecimal("1.50"));
    }
}
