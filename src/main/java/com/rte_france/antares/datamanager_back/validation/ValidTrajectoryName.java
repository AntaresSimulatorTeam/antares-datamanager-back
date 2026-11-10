package com.rte_france.antares.datamanager_back.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTrajectoryName {
    /**
     * Maximum name length after excluding one recognized leading trajectory prefix.
     * Prefix matching is case-insensitive; the original argument remains unchanged.
     */
    int maxLength() default TrajectoryValidator.DEFAULT_MAX_LENGTH;
}
