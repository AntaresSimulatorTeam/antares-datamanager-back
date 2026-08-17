package com.rte_france.antares.datamanager_back.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class TrajectoryValidationAspect {

    private final TrajectoryValidator trajectoryValidator;

    @Before("execution(* com.rte_france.antares.datamanager_back.controller..*(..))")
    public void validateTrajectoryName(JoinPoint joinPoint) {
        Method method = getMethod(joinPoint);
        if (method == null) {
            return;
        }

        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            
            if (parameter.isAnnotationPresent(ValidTrajectoryName.class)) {
                if (i < args.length && args[i] instanceof String) {
                    ValidTrajectoryName annotation = parameter.getAnnotation(ValidTrajectoryName.class);
                    String trajectoryName = (String) args[i];
                    int maxLength = annotation.maxLength();
                    trajectoryValidator.validate(trajectoryName, maxLength);
                }
            }
        }
    }

    private Method getMethod(JoinPoint joinPoint) {
        try {
            String methodName = joinPoint.getSignature().getName();
            Class<?> targetClass = joinPoint.getTarget().getClass();
            
            for (Method method : targetClass.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return method;
                }
            }
            
            for (Method method : targetClass.getMethods()) {
                if (method.getName().equals(methodName)) {
                    return method;
                }
            }
        } catch (Exception e) {
            log.debug("Could not find method for trajectory validation", e);
        }
        return null;
    }
}

