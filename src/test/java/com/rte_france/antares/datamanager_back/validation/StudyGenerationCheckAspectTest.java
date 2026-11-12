package com.rte_france.antares.datamanager_back.validation;

import com.rte_france.antares.datamanager_back.annotation.CheckStudyGenerationInProgress;
import com.rte_france.antares.datamanager_back.exception.AntaresErrorCode;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyStatus;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyGenerationCheckAspectTest {

    @Mock
    private StudyRepository studyRepository;

    @InjectMocks
    private StudyGenerationCheckAspect aspect;

    private StudyEntity studyEntity;

    @BeforeEach
    void setUp() {
        studyEntity = new StudyEntity();
        studyEntity.setId(1);
        studyEntity.setName("Test Study");
        studyEntity.setStatus(StudyStatus.IN_PROGRESS);
        studyEntity.setGenerationStartedBy("testuser");
    }

    private JoinPoint setupJoinPoint(String methodName, Object... args) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getTarget()).thenReturn(new TestController());
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    // Status validation tests

    @Test
    void checkStudyGenerationInProgress_whenStudyNotInGenerationProgress_doesNotThrow() {
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParam", 1)));
    }

    @Test
    void checkStudyGenerationInProgress_whenStudyInGenerationProgress_throwsBusinessException() {
        studyEntity.setStatus(StudyStatus.GENERATION_INPROGRESS);
        studyEntity.setGenerationStartedBy("anotheruser");
        
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        BusinessException exception = assertThrows(BusinessException.class, 
            () -> aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParam", 1)));
        
        assertEquals(AntaresErrorCode.STUDY_GENERATION_IN_PROGRESS, exception.getAntaresErrorCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    }

    @Test
    void checkStudyGenerationInProgress_whenStudyInGenerationProgress_withNullGeneratingUser_usesUnknown() {
        studyEntity.setStatus(StudyStatus.GENERATION_INPROGRESS);
        studyEntity.setGenerationStartedBy(null);
        
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        BusinessException exception = assertThrows(BusinessException.class, 
            () -> aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParam", 1)));
        
        assertEquals(AntaresErrorCode.STUDY_GENERATION_IN_PROGRESS, exception.getAntaresErrorCode());
        assertEquals("unknown", exception.getErrorMessageArguments().get(1));
    }

    @Test
    void checkStudyGenerationInProgress_whenStudyAlreadyGenerated_throwsBusinessException() {
        studyEntity.setStatus(StudyStatus.GENERATED);
        
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        BusinessException exception = assertThrows(BusinessException.class, 
            () -> aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParam", 1)));
        
        assertEquals(AntaresErrorCode.STUDY_ALREADY_GENERATED, exception.getAntaresErrorCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    }

    @Test
    void checkStudyGenerationInProgress_whenStudyNotFound_doesNotThrow() {
        when(studyRepository.findById(999)).thenReturn(Optional.empty());
        
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParam", 999)));
    }

    // RequestParam extraction tests

    @Test
    void checkStudyGenerationInProgress_withRequestParamValue_extractsStudyId() {
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParam", 1)));
        verify(studyRepository).findById(1);
    }

    @Test
    void checkStudyGenerationInProgress_withRequestParamEmptyValue_usesParameterName() {
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParamEmptyValue", 1)));
        verify(studyRepository).findById(1);
    }

    @Test
    void checkStudyGenerationInProgress_withRequestParamNameAttribute_usesNameAttribute() {
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParamNameAttribute", 1)));
        verify(studyRepository).findById(1);
    }

    // PathVariable extraction tests

    @Test
    void checkStudyGenerationInProgress_withPathVariable_extractsStudyId() {
        when(studyRepository.findById(42)).thenReturn(Optional.of(studyEntity));
        
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithPathVariable", 42)));
        verify(studyRepository).findById(42);
    }

    @Test
    void checkStudyGenerationInProgress_withPathVariableEmptyName_usesParameterName() {
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithPathVariableEmptyName", 1)));
        verify(studyRepository).findById(1);
    }

    @Test
    void checkStudyGenerationInProgress_withPathVariableNameAttribute_usesNameAttribute() {
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithPathVariableNameAttribute", 1)));
        verify(studyRepository).findById(1);
    }

    // Multiple parameters tests

    @Test
    void checkStudyGenerationInProgress_withMultipleParameters_extractsCorrectStudyId() {
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithMultipleParameters", "dummy", 1, "another")));
        verify(studyRepository).findById(1);
    }

    // Non-matching parameter tests

    @Test
    void checkStudyGenerationInProgress_withNonIntegerParameter_doesNotExtractStudyId() {
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParam", "not-an-integer")));
        verify(studyRepository, never()).findById(any());
    }

    @Test
    void checkStudyGenerationInProgress_withNullArg_doesNotExtractStudyId() {
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParam", (Object) null)));
        verify(studyRepository, never()).findById(any());
    }

    @Test
    void checkStudyGenerationInProgress_withAllParametersNonInteger_doesNotExtractStudyId() {
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithMultipleParameters", "study", "another", "value")));
        verify(studyRepository, never()).findById(any());
    }

    // Error handling tests

    @Test
    void checkStudyGenerationInProgress_whenGetMethodThrowsException_logsAndReturns() {
        JoinPoint badJoinPoint = mock(JoinPoint.class);
        when(badJoinPoint.getSignature()).thenThrow(new RuntimeException("Reflection error"));
        
        assertDoesNotThrow(() -> aspect.checkStudyGenerationInProgress(badJoinPoint));
    }

    @Test
    void checkStudyGenerationInProgress_whenMethodNotFound_logsAndReturns() {
        Object unknownTarget = new Object();
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("nonExistentMethod");
        when(joinPoint.getTarget()).thenReturn(unknownTarget);
        
        assertDoesNotThrow(() -> aspect.checkStudyGenerationInProgress(joinPoint));
        verify(studyRepository, never()).findById(any());
    }

    @Test
    void checkStudyGenerationInProgress_whenStudyIdCannotBeExtracted_doesNotThrow() {
        assertDoesNotThrow(() -> 
            aspect.checkStudyGenerationInProgress(setupJoinPoint("methodWithRequestParam", "string")));
        verify(studyRepository, never()).findById(any());
    }

    // Test controller with various method signatures
    static class TestController {
        @CheckStudyGenerationInProgress("id")
        public void methodWithRequestParam(@RequestParam("id") Integer id) {
        }

        @CheckStudyGenerationInProgress("studyId")
        public void methodWithPathVariable(@PathVariable("studyId") Integer studyId) {
        }

        @CheckStudyGenerationInProgress("id")
        public void methodWithMultipleParameters(String param1, @RequestParam("id") Integer id, String param3) {
        }

        @CheckStudyGenerationInProgress("studyId")
        public void methodWithPathVariableEmptyName(@PathVariable Integer studyId) {
        }

        @CheckStudyGenerationInProgress("id")
        public void methodWithRequestParamEmptyValue(@RequestParam Integer id) {
        }

        @CheckStudyGenerationInProgress("id")
        public void methodWithRequestParamNameAttribute(@RequestParam(name = "id") Integer parameter) {
        }

        @CheckStudyGenerationInProgress("studyId")
        public void methodWithPathVariableNameAttribute(@PathVariable(name = "studyId") Integer parameter) {
        }
    }
}
