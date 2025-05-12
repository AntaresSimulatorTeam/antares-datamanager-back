package com.rte_france.antares.datamanager_back.service.impl;

import lombok.Data;

import java.io.Serial;

@Data
public class SearchCriteria implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1905122041950251207L;

    private String key;
    private String operation;
    private Object value;

    public SearchCriteria(String key, String operation, Object value) {
        this.key = key;
        this.operation = operation;
        this.value = value;
    }
}
