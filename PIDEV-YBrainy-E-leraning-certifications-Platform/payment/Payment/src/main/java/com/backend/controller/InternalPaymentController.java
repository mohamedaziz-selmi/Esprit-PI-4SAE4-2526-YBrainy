package com.backend.controller;

import com.backend.dto.payment.StripeCheckoutSessionRequestDTO;
import com.backend.dto.payment.StripeCheckoutSessionResponseDTO;
import com.backend.dto.payment.StripeCheckoutVerificationRequestDTO;
import com.backend.dto.payment.StripeCheckoutVerificationResponseDTO;
import com.backend.service.StripeCheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/payments/stripe")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final StripeCheckoutService stripeCheckoutService;

    @PostMapping("/session")
    public ResponseEntity<StripeCheckoutSessionResponseDTO> createStripeCheckoutSession(
            @Valid @RequestBody StripeCheckoutSessionRequestDTO request) {
        return ResponseEntity.ok(stripeCheckoutService.createCheckoutSession(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<StripeCheckoutVerificationResponseDTO> verifyStripeCheckout(
            @Valid @RequestBody StripeCheckoutVerificationRequestDTO request) {
        return ResponseEntity.ok(StripeCheckoutVerificationResponseDTO.builder()
                .paid(stripeCheckoutService.isCheckoutSessionPaid(request.getSessionId()))
                .build());
    }
}
