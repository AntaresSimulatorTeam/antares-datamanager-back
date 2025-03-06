package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.service.WarningMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarningMessageServiceImpl implements WarningMessageService {

    private final MessageSource messageSource;

    @Override
    public String getMessage(String code, Object... args) {
        return messageSource.getMessage(code, args, code, Locale.getDefault());
    }

    @Override
    public String getNotFoundMessage() {

        return getMessage("data.not.found");
    }
}
