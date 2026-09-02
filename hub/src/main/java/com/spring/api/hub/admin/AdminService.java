package com.spring.api.hub.admin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void dropTableIfExists(String tableName) {
        // 1. Sanitize input to allow only alphanumeric characters and underscores
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid table name structure");
        }

        // 2. Construct and execute the dynamic native query
        String sql = "DROP TABLE IF EXISTS " + tableName;
        entityManager.createNativeQuery(sql).executeUpdate();
    }
}
