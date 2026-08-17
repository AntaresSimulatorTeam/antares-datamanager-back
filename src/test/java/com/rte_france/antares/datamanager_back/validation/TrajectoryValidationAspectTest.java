package com.rte_france.antares.datamanager_back.validation;

import com.rte_france.antares.datamanager_back.exception.AntaresErrorCode;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrajectoryValidationAspectTest {

    @Mock
    private TrajectoryValidator trajectoryValidator;

    @InjectMocks
    private TrajectoryValidationAspect aspect;

    private JoinPoint joinPoint;
    private Signature signature;

    @BeforeEach
    void setUp() {
        joinPoint = mock(JoinPoint.class);
        signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @Test
    void validateTrajectoryName_withValidAnnotatedParameter_callsValidator() {
        String trajectoryName = "valid-trajectory";
        TestController controller = new TestController();
        
        when(joinPoint.getTarget()).thenReturn(controller);
        when(signature.getName()).thenReturn("methodWithValidTrajectoryName");
        when(joinPoint.getArgs()).thenReturn(new Object[]{trajectoryName});

        aspect.validateTrajectoryName(joinPoint);

        verify(trajectoryValidator).validate(trajectoryName, 40);
    }

    @Test
    void validateTrajectoryName_withCustomMaxLength_passesMaxLengthToValidator() {
        String trajectoryName = "trajectory";
        TestController controller = new TestController();
        
        when(joinPoint.getTarget()).thenReturn(controller);
        when(signature.getName()).thenReturn("methodWithCustomMaxLength");
        when(joinPoint.getArgs()).thenReturn(new Object[]{trajectoryName});

        aspect.validateTrajectoryName(joinPoint);

        verify(trajectoryValidator).validate(trajectoryName, 50);
    }

    @Test
    void validateTrajectoryName_withMultipleParameters_validatesOnlyAnnotatedStringParameters() {
        String trajectoryName = "trajectory";
        Integer someNumber = 123;
        TestController controller = new TestController();
        
        when(joinPoint.getTarget()).thenReturn(controller);
        when(signature.getName()).thenReturn("methodWithMultipleParameters");
        when(joinPoint.getArgs()).thenReturn(new Object[]{trajectoryName, someNumber});

        aspect.validateTrajectoryName(joinPoint);

        verify(trajectoryValidator).validate(trajectoryName, 40);
    }

    @Test
    void validateTrajectoryName_withNonStringAnnotatedParameter_doesNotValidate() {
        TestController controller = new TestController();
        
        when(joinPoint.getTarget()).thenReturn(controller);
        when(signature.getName()).thenReturn("methodWithNonStringParameter");
        when(joinPoint.getArgs()).thenReturn(new Object[]{123});

        aspect.validateTrajectoryName(joinPoint);

        verify(trajectoryValidator, never()).validate(anyString(), anyInt());
    }

    @Test
    void validateTrajectoryName_withTooFewArguments_doesNotThrowException() {
        TestController controller = new TestController();
        
        when(joinPoint.getTarget()).thenReturn(controller);
        when(signature.getName()).thenReturn("methodWithValidTrajectoryName");
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        assertDoesNotThrow(() -> aspect.validateTrajectoryName(joinPoint));
        
        verify(trajectoryValidator, never()).validate(anyString(), anyInt());
    }

    @Test
    void validateTrajectoryName_withNullArgument_doesNotValidate() {
        TestController controller = new TestController();
        
        when(joinPoint.getTarget()).thenReturn(controller);
        when(signature.getName()).thenReturn("methodWithValidTrajectoryName");
        when(joinPoint.getArgs()).thenReturn(new Object[]{null});

        aspect.validateTrajectoryName(joinPoint);

        verify(trajectoryValidator, never()).validate(anyString(), anyInt());
    }

    @Test
    void validateTrajectoryName_whenMethodNotFound_doesNotThrowException() {
        Object unknownTarget = new Object();
        
        when(joinPoint.getTarget()).thenReturn(unknownTarget);
        when(signature.getName()).thenReturn("nonExistentMethod");

        assertDoesNotThrow(() -> aspect.validateTrajectoryName(joinPoint));
        
        verify(trajectoryValidator, never()).validate(anyString(), anyInt());
    }

    @Test
    void validateTrajectoryName_whenValidatorThrowsException_propagatesException() {
        String trajectoryName = "invalid-trajectory";
        TestController controller = new TestController();
        
        when(joinPoint.getTarget()).thenReturn(controller);
        when(signature.getName()).thenReturn("methodWithValidTrajectoryName");
        when(joinPoint.getArgs()).thenReturn(new Object[]{trajectoryName});
        
        BusinessException exception = BusinessException.builder()
                .antaresErrorCode(AntaresErrorCode.INVALID_TRAJECTORY_NAME)
                .message("Trajectory name too long")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
        doThrow(exception).when(trajectoryValidator).validate(trajectoryName, 40);

        assertThrows(BusinessException.class, () -> aspect.validateTrajectoryName(joinPoint));
    }

    @Test
    void validateTrajectoryName_withNoAnnotatedParameters_doesNotCallValidator() {
        TestController controller = new TestController();
        
        when(joinPoint.getTarget()).thenReturn(controller);
        when(signature.getName()).thenReturn("methodWithoutAnnotation");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"some-value"});

        aspect.validateTrajectoryName(joinPoint);

        verify(trajectoryValidator, never()).validate(anyString(), anyInt());
    }

    @Test
    void validateTrajectoryName_withMultipleAnnotatedParameters_validatesAll() {
        String trajectory1 = "trajectory1";
        String trajectory2 = "trajectory2";
        TestController controller = new TestController();
        
        when(joinPoint.getTarget()).thenReturn(controller);
        when(signature.getName()).thenReturn("methodWithMultipleAnnotatedParameters");
        when(joinPoint.getArgs()).thenReturn(new Object[]{trajectory1, trajectory2});

        aspect.validateTrajectoryName(joinPoint);

        verify(trajectoryValidator).validate(trajectory1, 40);
        verify(trajectoryValidator).validate(trajectory2, 40);
    }

    /* Test Controller with various method signatures for testing */
    public static class TestController {
        
        public void methodWithValidTrajectoryName(@ValidTrajectoryName String trajectoryName) {
        }

        public void methodWithCustomMaxLength(@ValidTrajectoryName(maxLength = 50) String trajectoryName) {
        }

        public void methodWithMultipleParameters(@ValidTrajectoryName String trajectoryName, Integer someNumber) {
        }

        public void methodWithNonStringParameter(@ValidTrajectoryName Integer nonStringParam) {
        }

        public void methodWithoutAnnotation(String trajectoryName) {
        }

        public void methodWithMultipleAnnotatedParameters(@ValidTrajectoryName String trajectory1, @ValidTrajectoryName String trajectory2) {
        }
    }
}
