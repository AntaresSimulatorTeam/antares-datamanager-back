package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.util.AreaNaming.AreaClusterKey;
import com.rte_france.antares.datamanager_back.util.AreaNaming.AreaKey;
import com.rte_france.antares.datamanager_back.util.AreaNaming.StudyAreas;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AreaNamingTest {

    @Nested
    class OthersTest {

        @ParameterizedTest
        @ValueSource(strings = {"OTHERS", "others", "Others", " OTHERS "})
        void isOthersWhateverTheCase(String areaName) {
            assertThat(AreaNaming.isOthers(areaName)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "OTHER", "FR", "OTHERS_FR"})
        void isNotOthers(String areaName) {
            assertThat(AreaNaming.isOthers(areaName)).isFalse();
        }

        @Test
        void othersConstantIsTheReservedName() {
            assertThat(AreaNaming.OTHERS).isEqualTo("OTHERS");
        }
    }

    @Nested
    class AreaKeyTest {

        @Test
        void sameKeyWhateverTheCase() {
            assertThat(AreaKey.of("DEkf")).isEqualTo(AreaKey.of("DEKF")).isEqualTo(AreaKey.of("dekf"));
            assertThat(AreaKey.of("DEkf")).hasSameHashCodeAs(AreaKey.of("dekf"));
            assertThat(AreaKey.of("DEkf").value()).isEqualTo("DEKF");
        }

        @Test
        void trimsOuterSpacesAndKeepsInnerOnes() {
            assertThat(AreaKey.of("  DEkf ")).isEqualTo(AreaKey.of("DEKF"));
            assertThat(AreaKey.of(" my area ").value()).isEqualTo("MY AREA");
            assertThat(AreaKey.of("my area")).isNotEqualTo(AreaKey.of("myarea"));
        }

        @Test
        void numericNamesAreValid() {
            assertThat(AreaKey.of("123").value()).isEqualTo("123");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        void rejectsNullAndBlank(String raw) {
            assertThatThrownBy(() -> AreaKey.of(raw)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void doesNotDependOnTheDefaultLocale() {
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                assertThat(AreaKey.of("city").value()).isEqualTo("CITY");
            } finally {
                Locale.setDefault(previous);
            }
        }

        @Test
        void worksAsMapKeyWithoutCaseSensitivity() {
            Map<AreaKey, String> byArea = new HashMap<>();
            byArea.put(AreaKey.of("DEKF"), "files");
            assertThat(byArea).containsEntry(AreaKey.of("DEkf"), "files");
        }

        @Test
        void toStringIsNotTheBareValue() {
            assertThat(AreaKey.of("DEkf")).hasToString("AreaKey[DEKF]");
        }
    }

    @Nested
    class AreaClusterKeyTest {

        @Test
        void sameAreaAndClusterWhateverTheCase() {
            assertThat(AreaClusterKey.of("DEkf", "Battery")).isEqualTo(AreaClusterKey.of("DEKF", "battery"));
            assertThat(AreaClusterKey.of("DEkf", "Battery"))
                    .hasSameHashCodeAs(AreaClusterKey.of("dekf", " BATTERY "));
        }

        @Test
        void differentClustersOrAreasAreDifferent() {
            assertThat(AreaClusterKey.of("DEkf", "gas")).isNotEqualTo(AreaClusterKey.of("DEkf", "coal"));
            assertThat(AreaClusterKey.of("DEkf", "gas")).isNotEqualTo(AreaClusterKey.of("FR", "gas"));
        }

        @Test
        void areaPrefixCollisionCannotMixClusters() {
            assertThat(AreaClusterKey.of("X", "Y_gas")).isNotEqualTo(AreaClusterKey.of("X_Y", "gas"));
        }

        @Test
        void rejectsInvalidParts() {
            assertThatThrownBy(() -> new AreaClusterKey(null, "gas")).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> AreaClusterKey.of("FR", " ")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AreaClusterKey.of(null, "gas")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void toStringIsNotTheBareValue() {
            assertThat(AreaClusterKey.of("DEkf", "gas")).hasToString("AreaClusterKey[DEKF, GAS]");
        }
    }

    @Nested
    class StudyAreasTest {

        @Test
        void returnsTheDisplayNameForAnyCaseOfTheKey() {
            StudyAreas areas = StudyAreas.of(List.of("DEkf", "FR"));

            assertThat(areas.contains(AreaKey.of("dekf"))).isTrue();
            assertThat(areas.displayName(AreaKey.of("DEKF"))).isEqualTo("DEkf");
        }

        @Test
        void trimsDisplayNames() {
            assertThat(StudyAreas.of(List.of(" DEkf ")).displayName(AreaKey.of("DEkf"))).isEqualTo("DEkf");
        }

        @Test
        void sameNameTwiceIsTolerated() {
            assertThat(StudyAreas.of(List.of("DEkf", "DEkf")).displayName(AreaKey.of("DEKF"))).isEqualTo("DEkf");
        }

        @Test
        void namesDifferingOnlyByCaseAreRejected() {
            List<String> names = List.of("DEkf", "DEKF");

            assertThatThrownBy(() -> StudyAreas.of(names))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DEkf")
                    .hasMessageContaining("DEKF");
        }

        @Test
        void unknownAreaIsAnError() {
            StudyAreas areas = StudyAreas.of(List.of("FR"));
            AreaKey unknown = AreaKey.of("DE");

            assertThat(areas.contains(unknown)).isFalse();
            assertThatThrownBy(() -> areas.displayName(unknown)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullAndBlankNames() {
            List<String> withNull = Arrays.asList("FR", null);
            List<String> withBlank = List.of(" ");

            assertThatThrownBy(() -> StudyAreas.of(withNull)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StudyAreas.of(withBlank)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void clusterDisplayNameKeepsTheAreaCaseAndTheSuffixAsGiven() {
            StudyAreas areas = StudyAreas.of(List.of("DEkf"));

            assertThat(areas.clusterDisplayName(AreaKey.of("DEKF"), "gas")).isEqualTo("DEkf_gas");
            assertThat(areas.clusterDisplayName(AreaKey.of("dekf"), "Gas_OCGT")).isEqualTo("DEkf_Gas_OCGT");
        }

        @Test
        void clusterDisplayNameRejectsBlankSuffixAndUnknownArea() {
            StudyAreas areas = StudyAreas.of(List.of("DEkf"));
            AreaKey known = AreaKey.of("DEkf");
            AreaKey unknown = AreaKey.of("FR");

            assertThatThrownBy(() -> areas.clusterDisplayName(known, " ")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> areas.clusterDisplayName(unknown, "gas")).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
