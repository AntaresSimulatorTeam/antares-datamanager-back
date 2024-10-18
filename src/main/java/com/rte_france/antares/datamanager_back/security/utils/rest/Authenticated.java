package com.rte_france.antares.datamanager_back.security.utils.rest;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Authenticated {
    private List<String> apiMatcher;
}
