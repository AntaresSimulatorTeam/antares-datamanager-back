package com.rte_france.antares.datamanager_back.service;

import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;

public class TestConstants {
    public static Stream<Arguments> booleanKeywords() {
        return Stream.of(
                Arguments.of("true", true), Arguments.of("vrai", true), Arguments.of("oui", true),
                Arguments.of("yes", true), Arguments.of("1", true),
                Arguments.of("false", false), Arguments.of("faux", false), Arguments.of("non", false),
                Arguments.of("no", false), Arguments.of("0", false)
        );
    }
}