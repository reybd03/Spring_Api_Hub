package com.spring.api.hub.products.automation;

public class AutomationStateEvent {
    private final Long productId;
    private final String ProductName;;

    public AutomationStateEvent(Long productId, String ProductName) {
        this.productId = productId;
        this.ProductName = ProductName;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return ProductName;
    }
}
