package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.WarningMessageRepository;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import com.rte_france.antares.datamanager_back.service.impl.WarningMessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WarningMessageServiceImplTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private WarningMessageRepository warningMessageRepository;

    @InjectMocks
    private WarningMessageServiceImpl warningMessageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getMessage_returnsCorrectMessage() {
        var code = "test.code";
        var expectedMessage = "Test message";
        when(messageSource.getMessage(eq(code), any(), eq(code), eq(Locale.getDefault()))).thenReturn(expectedMessage);

        var actualMessage = warningMessageService.getMessage(code);

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void getNotFoundMessage_returnsCorrectMessage() {
        var code = "data.not.found";
        var expectedMessage = "Data not found";
        when(messageSource.getMessage(eq(code), any(), eq(code), eq(Locale.getDefault()))).thenReturn(expectedMessage);

        var actualMessage = warningMessageService.getNotFoundMessage();

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void acknowledgeWarning_updatesIsAckToTrue_whenWarningExists() {
        var id = 1;
        var warning = new WarningMessageEntity();
        warning.setId(id);
        warning.setIsAck(false);

        when(warningMessageRepository.findById(id)).thenReturn(Optional.of(warning));

        warningMessageService.acknowledgeWarning(id);

        assertEquals(true, warning.getIsAck());
        verify(warningMessageRepository).save(warning);
    }

    @Test
    void acknowledgeWarning_throwsBusinessException_whenWarningDoesNotExist() {
        var id = 1;

        when(warningMessageRepository.findById(id)).thenReturn(Optional.empty());

        var exception = assertThrows(BusinessException.class, () -> warningMessageService.acknowledgeWarning(id));

        assertEquals("Warning message not found with id: " + id, exception.getMessage());
        verify(warningMessageRepository, never()).save(any());
    }
}