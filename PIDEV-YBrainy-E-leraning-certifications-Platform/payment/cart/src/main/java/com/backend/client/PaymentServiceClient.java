package com.backend.client;

import com.backend.dto.cart.StripeCheckoutSessionResponseDTO;
import com.backend.dto.payment.PaymentPackResponseDTO;
import com.backend.dto.payment.PaymentStripeCheckoutSessionRequestDTO;
import com.backend.dto.payment.PaymentStripeCheckoutVerificationRequestDTO;
import com.backend.dto.payment.PaymentStripeCheckoutVerificationResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service")
public interface PaymentServiceClient {

    @GetMapping("/api/packs/{id}")
    PaymentPackResponseDTO getActivePackById(@PathVariable("id") Long id);

    @PostMapping("/api/internal/payments/stripe/session")
    StripeCheckoutSessionResponseDTO createStripeCheckoutSession(
            @RequestBody PaymentStripeCheckoutSessionRequestDTO request);

    @PostMapping("/api/internal/payments/stripe/verify")
    PaymentStripeCheckoutVerificationResponseDTO verifyStripeCheckout(
            @RequestBody PaymentStripeCheckoutVerificationRequestDTO request);
}
