package com.rte_france.antares.datamanager_back.configuration.logback;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final String HTTP_REQUEST_METHOD_KEY = "http.request.method";
    private static final String HTTP_REQUEST_URI = "http.request.uri";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        MDC.put(HTTP_REQUEST_METHOD_KEY, request.getMethod());
        MDC.put(HTTP_REQUEST_URI, request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
