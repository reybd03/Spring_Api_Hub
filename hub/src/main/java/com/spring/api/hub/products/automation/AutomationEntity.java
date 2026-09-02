package com.spring.api.hub.products.automation;

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
@Data // Generates getters and setters for all fields. Can cause StackOverflow error
      // if not handled properly.
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "automation")
public class AutomationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false, unique = true)
    private String productName = "Product";

    private String productDiscoveryStatus = "Initial"; // Initial, Completed, Pending_Discovery, Discovering, Failed
    private boolean productDiscovered = false;
    private String productBasePath = "Not Found";
    private String productUserName = "Not Found";
    private String productPassword = "Not Found";
    private String productAPIKey = "Not Found";
    private String productURL = "Not Found";
    private String productPort = "Not Found";
    private String productStatus = "Unknown";
    private String productFunctions = "Unknown"; // Functions of product e.g. Build, Test, Deploy
    private String productActions = "Unknown"; // Actions of product e.g. Start, Stop, Restart

    // public Long getId() {
    // return id;
    // }

    // public void setId(Long id) {
    // this.id = id;
    // }

    // public String getProductDiscoveryStatus() {
    // return productDiscoveryStatus;
    // }

    // public void setProductDiscoveryStatus(String productDiscoveryStatus) {
    // this.productDiscoveryStatus = productDiscoveryStatus;
    // }

    public boolean getProductDiscovered() {
        return productDiscovered;
    }

    public void setProductDiscovered(boolean productDiscovered) {
        this.productDiscovered = productDiscovered;
    }

    // public String getProductBasePath() {
    // return productBasePath;
    // }

    // public void setProductBasePath(String productBasePath) {
    // this.productBasePath = productBasePath;
    // }

    // public String getProductUserName() {
    // return productUserName;
    // }

    // public void setProductUserName(String productUserName) {
    // this.productUserName = productUserName;
    // }

    // public String getProductPassword() {
    // return productPassword;
    // }

    // public void setProductPassword(String productPassword) {
    // this.productPassword = productPassword;
    // }

    // public String getProductAPIKey() {
    // return productAPIKey;
    // }

    // public void setProductAPIKey(String productAPIKey) {
    // this.productAPIKey = productAPIKey;
    // }

    // public String getProductURL() {
    // return productURL;
    // }

    // public void setProductURL(String productURL) {
    // this.productURL = productURL;
    // }

    // public String getProductName() {
    // return productName;
    // }

    // public void setProductName(String productName) {
    // this.productName = productName;
    // }

    // public String getProductPort() {
    // return productPort;
    // }

    // public void setProductPort(String productPort) {
    // this.productPort = productPort;
    // }

    // public String getProductStatus() {
    // return productStatus;
    // }

    // public void setProductStatus(String productStatus) {
    // this.productStatus = productStatus;
    // }

    // public String getProductFunctions() {
    // return productFunctions;
    // }

    // public void setProductFunctions(String productFunctions) {
    // this.productFunctions = productFunctions;
    // }

    // public String getProductActions() {
    // return productActions;
    // }

    // public void setProductActions(String productActions) {
    // this.productActions = productActions;
    // }

}
