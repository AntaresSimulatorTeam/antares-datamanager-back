package com.rte_france.antares.datamanager_back.service.impl;

import lombok.Data;

@Data
public class SearchCriteria implements java.io.Serializable {
    private String key;
    private String operation;
    private Object value;

    public SearchCriteria(String key, String operation, Object value) {
        this.key = key;
        this.operation = operation;
        this.value = value;
    }
}
