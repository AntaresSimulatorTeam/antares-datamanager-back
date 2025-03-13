package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.service.impl.WarningMessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class WarningMessageServiceImplTest {

    @Mock
    private MessageSource messageSource;

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
}