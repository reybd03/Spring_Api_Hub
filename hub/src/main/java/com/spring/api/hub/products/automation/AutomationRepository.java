package com.spring.api.hub.products.automation;

// import java.util.List;
import java.util.Optional;

import com.spring.api.hub.products.automation.AutomationEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AutomationRepository extends JpaRepository<AutomationEntity, Long> {

    // List<AutomationEntity> findByProductName(String ProductName);

    // boolean existsByProductName(String ProductName);

    Optional<AutomationEntity> findByProductName(String ProductName);

    @Modifying
    @Transactional
    @Query(value = "DELETE p1 FROM product p1 " +
            "INNER JOIN product p2 " +
            "WHERE p1.id > p2.id AND p1.product_name = p2.product_name", nativeQuery = true)
    int deleteDuplicatesByProductName();
}
