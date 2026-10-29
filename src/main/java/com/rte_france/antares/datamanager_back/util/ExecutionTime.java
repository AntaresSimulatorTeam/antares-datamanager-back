package com.rte_france.antares.datamanager_back.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour tracer le temps d'exécution des méthodes.
 * Génère des logs structurés compatibles ELK avec hiérarchie d'exécution.
 * 
 * Exemple: @ExecutionTime(description = "Génération des données de charge")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExecutionTime {
    /**
     * Description optionnelle de l'exécution pour les logs
     */
    String description() default "";
    
    /**
     * Indique si c'est une étape majeure (racine de trace)
     */
    boolean isRootStep() default false;
}
