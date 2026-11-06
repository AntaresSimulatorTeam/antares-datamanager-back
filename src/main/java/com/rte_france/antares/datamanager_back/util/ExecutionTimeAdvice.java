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
        String methodName = joinPoint.getSignature().getName();
        
        extractAndSetStudyIdToMdc(joinPoint);
        
        String executionContext = executionTime.value();
        String previousContext = null;
        
        if (executionContext != null && !executionContext.isEmpty()) {
            previousContext = MDC.get("executionContext");
            MDC.put("executionContext", executionContext);
        }

        try {
            Object proceed = joinPoint.proceed();
            
            long executionTimeMs = System.currentTimeMillis() - start;
            long executionTimeInSeconds = executionTimeMs / 1000;
            MDC.put("executionTime", String.valueOf(executionTimeInSeconds));
            MDC.put("methodName", methodName);
            log.info("{} executed in {}ms ({}s)", methodName, executionTimeMs, executionTimeInSeconds);
            
            return proceed;
        } finally {
            if (executionContext != null && !executionContext.isEmpty()) {
                if (previousContext != null) {
                    MDC.put("executionContext", previousContext);
                } else {
                    MDC.remove("executionContext");
                }
            }
        }
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
