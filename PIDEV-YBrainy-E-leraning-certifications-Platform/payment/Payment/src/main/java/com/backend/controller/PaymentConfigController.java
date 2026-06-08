package com.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentConfigController {

    @Value("${stripe.publishable-key:}")
    private String stripePublishableKey;

    @GetMapping("/config")
    public Map<String, String> getStripeConfig() {
        return Map.of("publishableKey", stripePublishableKey == null ? "" : stripePublishableKey);
    }
}
