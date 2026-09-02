package com.spring.api.hub.products.automation;

import com.spring.api.hub.products.automation.AutomationStateEvent;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AutomationStateListener {

    private static ApplicationEventPublisher eventPublisher;

    public AutomationStateListener(ApplicationEventPublisher eventPublisher) {
        AutomationStateListener.eventPublisher = eventPublisher;
    }

    @PostPersist
    @PostUpdate
    public void onPostPersist(AutomationEntity automationEntity) {
        // Initial, Completed, Pending_Discovery, Discovering, Failed
        // Condition: Trigger discovery only if state equals "Pending_Discovery"
        System.out.println("here");
        if ("Pending_Discovery".equals(automationEntity.getProductDiscoveryStatus())) {
            System.out.println("here2");
            eventPublisher.publishEvent(
                    new AutomationStateEvent(automationEntity.getId(), automationEntity.getProductName()));
        }
    }
}
