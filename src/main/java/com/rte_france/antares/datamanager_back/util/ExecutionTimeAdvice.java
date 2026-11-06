package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ExecutionTimeAdvice {

    private static final String STUDY_ID_KEY = "studyId";

    @Pointcut("@annotation(com.rte_france.antares.datamanager_back.util.ExecutionTime)")
    public void executionTimePointcut() {
    }

    @Around("executionTimePointcut() && @annotation(executionTime)")
    public Object executionTime(ProceedingJoinPoint joinPoint, ExecutionTime executionTime) throws Throwable {
        long start = System.currentTimeMillis();

        extractAndSetStudyIdToMdc(joinPoint);
        
        // Ajouter le contexte d'exécution au MDC
        String executionContext = executionTime.value();
        if (executionContext != null && !executionContext.isEmpty()) {
            MDC.put("executionContext", executionContext);
        }

            Object proceed = joinPoint.proceed();

            long executionTimeMs = System.currentTimeMillis() - start;
            //en seconds
            long executionTimeInSeconds = executionTimeMs / 1000;
            MDC.put("executionTime", String.valueOf(executionTimeInSeconds));
            MDC.put("methodName", joinPoint.getSignature().getName());
            log.info("{} executed in {}ms ({}s)", joinPoint.getSignature().getName(), executionTimeMs, executionTimeInSeconds);
            return proceed;

    }

    private void extractAndSetStudyIdToMdc(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();

        for (Object arg : args) {
            if (arg instanceof StudyEntity studyEntity) {
                Integer studyId = studyEntity.getId();
                if (studyId != null) {
                    MDC.put(STUDY_ID_KEY, studyId.toString());
                    break;
                }
            }
        }
    }
}
