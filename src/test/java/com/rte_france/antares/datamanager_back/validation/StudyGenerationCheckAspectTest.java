package com.rte_france.antares.datamanager_back.validation;

import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.AntaresErrorCode;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyStatus;
import com.rte_france.antares.datamanager_back.service.study.StudyService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    void checkStudyGenerationInProgress_whenStudyNotInGenerationProgress_doesNotThrow() {
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        assertDoesNotThrow(() -> {
            org.aspectj.lang.JoinPoint joinPoint = mock(org.aspectj.lang.JoinPoint.class);
            org.aspectj.lang.Signature signature = mock(org.aspectj.lang.Signature.class);
            
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getName()).thenReturn("generateStudy");
            when(joinPoint.getTarget()).thenReturn(new TestController());
            when(joinPoint.getArgs()).thenReturn(new Object[]{1});
            
            // Don't throw, just verify the method completes
            aspect.checkStudyGenerationInProgress(joinPoint);
        });
    }

    @Test
    void checkStudyGenerationInProgress_whenStudyInGenerationProgress_throwsBusinessException() {
        studyEntity.setStatus(StudyStatus.GENERATION_INPROGRESS);
        studyEntity.setGenerationStartedBy("anotheruser");
        
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            org.aspectj.lang.JoinPoint joinPoint = mock(org.aspectj.lang.JoinPoint.class);
            org.aspectj.lang.Signature signature = mock(org.aspectj.lang.Signature.class);
            
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getName()).thenReturn("generateStudy");
            when(joinPoint.getTarget()).thenReturn(new TestController());
            when(joinPoint.getArgs()).thenReturn(new Object[]{1});
            
            aspect.checkStudyGenerationInProgress(joinPoint);
        });
        
        assertEquals(AntaresErrorCode.STUDY_GENERATION_IN_PROGRESS, exception.getAntaresErrorCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("being generated"));
    }

    @Test
    void checkStudyGenerationInProgress_whenStudyNotFound_doesNotThrow() {
        when(studyRepository.findById(999)).thenReturn(Optional.empty());
        
        assertDoesNotThrow(() -> {
            org.aspectj.lang.JoinPoint joinPoint = mock(org.aspectj.lang.JoinPoint.class);
            org.aspectj.lang.Signature signature = mock(org.aspectj.lang.Signature.class);
            
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getName()).thenReturn("generateStudy");
            when(joinPoint.getTarget()).thenReturn(new TestController());
            when(joinPoint.getArgs()).thenReturn(new Object[]{999});
            
            aspect.checkStudyGenerationInProgress(joinPoint);
        });
    }

    static class TestController {
        @com.rte_france.antares.datamanager_back.annotation.CheckStudyGenerationInProgress
        public void generateStudy(@org.springframework.web.bind.annotation.RequestParam("id") Integer id) {
            // Test method
        }
    }
}
