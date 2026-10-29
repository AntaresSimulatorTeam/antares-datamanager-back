package com.rte_france.antares.datamanager_back.util;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Aspect pour tracer l'exécution des méthodes annotées avec @ExecutionTime.
 * Génère des logs structurés compatibles ELK avec:
 * - Temps total d'exécution
 * - Hiérarchie des appels (trace_id, step_id, parent_step_id)
 * - Métriques détaillées pour chaque étape
 */
@Slf4j
@Aspect
@Component
public class ExecutionTimeAdvice {

    private static final String TRACE_ID = "trace_id";
    private static final String STEP_ID = "step_id";
    private static final String PARENT_STEP_ID = "parent_step_id";
    private static final String EXECUTION_DEPTH = "execution_depth";
    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Stack<ExecutionContext>> CONTEXT_STACK = ThreadLocal.withInitial(Stack::new);

    @Pointcut("@annotation(com.rte_france.antares.datamanager_back.util.ExecutionTime)")
    public void executionTimePointcut() {
    }

    @Around("executionTimePointcut()")
    public Object executionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        ExecutionTime annotation = getAnnotation(joinPoint);
        String methodName = joinPoint.getSignature().getName();
        String description = annotation.description().isEmpty() ? methodName : annotation.description();
        boolean isRootStep = annotation.isRootStep();

        // Initialiser ou récupérer le trace_id
        String traceId = TRACE_ID_HOLDER.get();
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            TRACE_ID_HOLDER.set(traceId);
        }

        // Gérer la profondeur et l'hiérarchie
        Integer depth = DEPTH_HOLDER.get();
        if (depth == null) {
            depth = 0;
        }

        // Générer un step_id unique
        String stepId = UUID.randomUUID().toString();
        
        // Récupérer le parent_step_id
        Stack<ExecutionContext> contextStack = CONTEXT_STACK.get();
        String parentStepId = null;
        if (!contextStack.isEmpty()) {
            parentStepId = contextStack.peek().getStepId();
        }

        // Créer le contexte d'exécution
        ExecutionContext context = new ExecutionContext(stepId, parentStepId, description, methodName, depth);
        contextStack.push(context);

        // Sauvegarder l'état précédent du MDC
        Map<String, String> mdcBackup = MDC.getCopyOfContextMap() != null ? 
            new HashMap<>(MDC.getCopyOfContextMap()) : new HashMap<>();

        // Initialiser les timestamps avant le try
        long startTime = System.nanoTime();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        try {
            // Définir les valeurs MDC pour les logs ELK
            MDC.put(TRACE_ID, traceId);
            MDC.put(STEP_ID, stepId);
            if (parentStepId != null) {
                MDC.put(PARENT_STEP_ID, parentStepId);
            }
            MDC.put(EXECUTION_DEPTH, String.valueOf(depth));

            // Log de démarrage
            log.info(
                "START_EXECUTION - méthode: {}, description: {}, depth: {}, parent_step: {}",
                methodName, description, depth, parentStepId != null ? parentStepId : "null"
            );

            // Incrémenter la profondeur pour les appels imbriqués
            DEPTH_HOLDER.set(depth + 1);

            Object result = joinPoint.proceed();

            long endTime = System.nanoTime();
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long executionTimeMs = (endTime - startTime) / 1_000_000;
            long memoryUsedMb = (endMemory - startMemory) / (1024 * 1024);

            // Log de succès avec métriques
            log.info(
                "END_EXECUTION_SUCCESS - méthode: {}, description: {}, execution_time_ms: {}, memory_used_mb: {}, depth: {}",
                methodName, description, executionTimeMs, memoryUsedMb, depth
            );

            context.setExecutionTimeMs(executionTimeMs);
            context.setMemoryUsedMb(memoryUsedMb);
            context.setStatus("SUCCESS");

            return result;

        } catch (Throwable throwable) {
            long endTime = System.nanoTime();
            long executionTimeMs = (endTime - startTime) / 1_000_000;

            // Log d'erreur avec contexte
            log.error(
                "END_EXECUTION_ERROR - méthode: {}, description: {}, execution_time_ms: {}, depth: {}, error: {}",
                methodName, description, executionTimeMs, depth, throwable.getMessage(),
                throwable
            );

            context.setExecutionTimeMs(executionTimeMs);
            context.setStatus("ERROR");
            context.setErrorMessage(throwable.getMessage());

            throw throwable;

        } finally {
            // Restaurer la profondeur
            DEPTH_HOLDER.set(depth);
            contextStack.pop();

            // Si c'est la dernière étape (pile vide et racine), générer le rapport
            if (contextStack.isEmpty() && isRootStep) {
                generateExecutionReport();
                TRACE_ID_HOLDER.remove();
                DEPTH_HOLDER.remove();
                CONTEXT_STACK.remove();
            }

            // Restaurer le MDC original
            MDC.clear();
            if (mdcBackup != null) {
                mdcBackup.forEach(MDC::put);
            }
        }
    }

    /**
     * Récupère l'annotation @ExecutionTime de la méthode
     */
    private ExecutionTime getAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod().getAnnotation(ExecutionTime.class);
    }

    /**
     * Génère un rapport d'exécution complet avec hiérarchie
     */
    private void generateExecutionReport() {
        Stack<ExecutionContext> contextStack = CONTEXT_STACK.get();
        if (!contextStack.isEmpty()) {
            // Récupérer toutes les données du stack (clone pour la lecture)
            List<ExecutionContext> allContexts = new ArrayList<>(contextStack);
            
            // Calculer le temps total
            long totalTimeMs = 0;
            long totalMemoryMb = 0;
            for (ExecutionContext ctx : allContexts) {
                if (ctx.getExecutionTimeMs() > 0) {
                    totalTimeMs += ctx.getExecutionTimeMs();
                    totalMemoryMb += ctx.getMemoryUsedMb();
                }
            }

            // Log de rapport global
            log.info(
                "EXECUTION_REPORT - trace_id: {}, total_execution_time_ms: {}, total_memory_mb: {}, steps_count: {}, status: {}",
                TRACE_ID_HOLDER.get(),
                totalTimeMs,
                totalMemoryMb,
                allContexts.size(),
                allContexts.stream().allMatch(c -> "SUCCESS".equals(c.getStatus())) ? "SUCCESS" : "FAILED"
            );

            // Log de détails des étapes
            for (ExecutionContext ctx : allContexts) {
                log.debug(
                    "STEP_DETAIL - step_id: {}, parent_step_id: {}, method: {}, description: {}, time_ms: {}, memory_mb: {}, status: {}",
                    ctx.getStepId(),
                    ctx.getParentStepId() != null ? ctx.getParentStepId() : "null",
                    ctx.getMethodName(),
                    ctx.getDescription(),
                    ctx.getExecutionTimeMs(),
                    ctx.getMemoryUsedMb(),
                    ctx.getStatus()
                );
            }
        }
    }

    /**
     * Classe interne pour gérer le contexte d'exécution
     */
    private static class ExecutionContext {
        private final String stepId;
        private final String parentStepId;
        private final String description;
        private final String methodName;
        private final int depth;
        private long executionTimeMs;
        private long memoryUsedMb;
        private String status = "PENDING";
        private String errorMessage;

        public ExecutionContext(String stepId, String parentStepId, String description, String methodName, int depth) {
            this.stepId = stepId;
            this.parentStepId = parentStepId;
            this.description = description;
            this.methodName = methodName;
            this.depth = depth;
        }

        public String getStepId() { return stepId; }
        public String getParentStepId() { return parentStepId; }
        public String getDescription() { return description; }
        public String getMethodName() { return methodName; }
        public int getDepth() { return depth; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
        public long getMemoryUsedMb() { return memoryUsedMb; }
        public void setMemoryUsedMb(long memoryUsedMb) { this.memoryUsedMb = memoryUsedMb; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
