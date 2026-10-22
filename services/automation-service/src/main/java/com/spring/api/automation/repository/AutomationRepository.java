package com.spring.api.automation.repository;

import com.spring.api.automation.entity.AutomationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AutomationRepository extends JpaRepository<AutomationEntity, Long> {

    Optional<AutomationEntity> findByProductName(String productName);

    List<AutomationEntity> findByProductNameContainingIgnoreCase(String productName);

    @Modifying
    @Transactional
    @Query(value = "DELETE a1 FROM automation a1 " +
            "INNER JOIN automation a2 " +
            "WHERE a1.id > a2.id AND a1.product_name = a2.product_name", nativeQuery = true)
    int deleteDuplicatesByProductName();

    boolean existsByProductName(String productName);
}