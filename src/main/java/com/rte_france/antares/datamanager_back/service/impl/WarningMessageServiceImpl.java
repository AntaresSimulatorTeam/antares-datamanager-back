package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.repository.model.WarningCode;
import com.rte_france.antares.datamanager_back.service.WarningMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarningMessageServiceImpl implements WarningMessageService {

    private final MessageSource messageSource;

    @Override
    public String getMessage(String code, Object... args) {
        String template = messageSource.getMessage(code, null, code, Locale.getDefault());
        assert template != null;
        return MessageFormat.format(template, args); // Properly replaces {0}, {1}, etc.
    }


    @Override
    public String getNotFoundMessage() {
        return getMessage(WarningCode.DATA_NOT_FOUND.value());
    }
}
