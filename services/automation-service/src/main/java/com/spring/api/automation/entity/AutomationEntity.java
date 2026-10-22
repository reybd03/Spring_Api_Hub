package com.spring.api.automation.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "automation")
public class AutomationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false, unique = true)
    private String productName = "Product";

    private String productDiscoveryStatus = "Initial";
    private boolean productDiscovered = false;
    private String productBasePath = "Not Found";
    private String productUserName = "Not Found";
    private String productPassword = "Not Found";
    private String productAPIKey = "Not Found";
    private String productURL = "Not Found";
    private String productPort = "Not Found";
    private Long productProcessId = -1L;
    private String productStatus = "Unknown";
    private String productFunctions = "Unknown";
    private String productActions = "Unknown";

    public boolean getProductDiscovered() {
        return productDiscovered;
    }

    public void setProductDiscovered(boolean productDiscovered) {
        this.productDiscovered = productDiscovered;
    }
}