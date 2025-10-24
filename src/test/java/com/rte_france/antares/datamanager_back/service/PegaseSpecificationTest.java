package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.service.common.impl.PegaseSpecification;
import com.rte_france.antares.datamanager_back.service.common.impl.SearchCriteria;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class PegaseSpecificationTest {

    @Mock
    private Root<Object> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Path<Object> path;

    private PegaseSpecification<Object> specification;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(root.get(anyString())).thenReturn(path);
    }

    @Test
    void toPredicate_Equal() {
        when(root.get("id")).thenReturn(path);

        SearchCriteria criteria = new SearchCriteria("id", ":", 100);
        specification = new PegaseSpecification<>(criteria);

        Predicate mockPredicate = mock(Predicate.class);
        when(builder.equal(path, 100)).thenReturn(mockPredicate);

        Predicate predicate = specification.toPredicate(root, query, builder);

        assertNotNull(predicate);
        verify(builder).equal(path, 100);
    }

    @Test
    void toPredicate_InvalidOperation() {
        when(root.get("name")).thenReturn(path);

        SearchCriteria criteria = new SearchCriteria("name", "invalid", "John");
        specification = new PegaseSpecification<>(criteria);

        Predicate predicate = specification.toPredicate(root, query, builder);

        assertNull(predicate);
    }


    @SuppressWarnings("unchecked")
    @Test
    void inOperationWithLikeOnCollection() {
        Join<Object, String> join = mock(Join.class);

        // Cast et retour explicite du join
        when(root.join("roles")).thenAnswer(invocation -> join);

        Expression<String> lowerExpr = mock(Expression.class);
        when(builder.lower(join)).thenReturn(lowerExpr);

        Predicate mockPredicate = mock(Predicate.class);
        when(builder.like(lowerExpr, "%admin%")).thenReturn(mockPredicate);

        SearchCriteria criteria = new SearchCriteria("roles", "in", "admin");
        specification = new PegaseSpecification<>(criteria);

        Predicate predicate = specification.toPredicate(root, query, builder);

        assertNotNull(predicate);
        verify(root).join("roles");
        verify(builder).lower(join);
        verify(builder).like(lowerExpr, "%admin%");
    }


}