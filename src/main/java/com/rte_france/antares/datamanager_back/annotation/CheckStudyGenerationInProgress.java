package com.rte_france.antares.datamanager_back.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckStudyGenerationInProgress {
    String[] value() default {"studyId", "id"};
}
