package com.rte_france.antares.datamanager_back.service.impl;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;


public class PegaseSpecification<T> implements Specification<T> {

    private final SearchCriteria criteria;

    public PegaseSpecification(final SearchCriteria criteria) {
        super();
        this.criteria = criteria;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
        if (criteria.getOperation().equalsIgnoreCase(">")) {
            return builder.greaterThanOrEqualTo(root.get(criteria.getKey()), criteria.getValue().toString());
        }
        else if (criteria.getOperation().equalsIgnoreCase("<")) {
            return builder.lessThanOrEqualTo(root.get(criteria.getKey()), criteria.getValue().toString());
        } else if (criteria.getOperation().equalsIgnoreCase("in")) {
            // Join avec les éléments de la liste 'tags'
            Join<T, String> join = root.join(criteria.getKey());
            return builder.like(
                    builder.lower(join),
                    "%" + criteria.getValue().toString().toLowerCase() + "%"
            );
        }else if (criteria.getOperation().equalsIgnoreCase(":")) {
            if (root.get(criteria.getKey()).getJavaType() == String.class) {
                return builder.like(builder.lower(root.<String>get(criteria.getKey())), "%" + criteria.getValue().toString().toLowerCase() + "%");
            } else {
                return builder.equal(root.get(criteria.getKey()), criteria.getValue());
            }
        }
        return null;
    }
}

