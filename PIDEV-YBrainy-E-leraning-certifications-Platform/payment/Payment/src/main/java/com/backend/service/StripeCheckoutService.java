package com.backend.service;

import com.backend.dto.payment.StripeCheckoutItemRequestDTO;
import com.backend.dto.payment.StripeCheckoutSessionRequestDTO;
import com.backend.dto.payment.StripeCheckoutSessionResponseDTO;
import com.backend.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StripeCheckoutService {

    private static final String STRIPE_API_BASE_URL = "https://api.stripe.com/v1";

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.publishable-key}")
    private String stripePublishableKey;

    @Value("${stripe.currency:usd}")
    private String stripeCurrency;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    public StripeCheckoutSessionResponseDTO createCheckoutSession(StripeCheckoutSessionRequestDTO request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("Cannot checkout an empty cart.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", successUrl);
        form.add("cancel_url", cancelUrl);
        form.add("client_reference_id", String.valueOf(request.getUserId()));
        form.add("metadata[user_id]", String.valueOf(request.getUserId()));
        form.add("metadata[cart_id]", String.valueOf(request.getCartId()));

        for (int i = 0; i < request.getItems().size(); i++) {
            StripeCheckoutItemRequestDTO item = request.getItems().get(i);
            long unitAmount = toMinorUnit(item.getPriceAtPurchase());

            form.add("line_items[" + i + "][price_data][currency]", stripeCurrency.toLowerCase());
            form.add("line_items[" + i + "][price_data][unit_amount]", String.valueOf(unitAmount));
            form.add("line_items[" + i + "][price_data][product_data][name]", safeProductName(item.getPackTitle()));
            form.add("line_items[" + i + "][quantity]", String.valueOf(item.getQuantity()));
        }

        JsonNode response = postForm("/checkout/sessions", form);
        String sessionId = response.path("id").asText(null);
        String checkoutUrl = response.path("url").asText(null);

        if (isBlank(sessionId) || isBlank(checkoutUrl)) {
            throw new BusinessRuleException("Stripe did not return a valid checkout session.");
        }

        return StripeCheckoutSessionResponseDTO.builder()
                .sessionId(sessionId)
                .checkoutUrl(checkoutUrl)
                .publishableKey(stripePublishableKey)
                .build();
    }

    public boolean isCheckoutSessionPaid(String sessionId) {
        if (isBlank(sessionId)) {
            return false;
        }

        JsonNode response = get("/checkout/sessions/" + sessionId);
        String paymentStatus = response.path("payment_status").asText("");
        String sessionStatus = response.path("status").asText("");

        return "paid".equalsIgnoreCase(paymentStatus) && "complete".equalsIgnoreCase(sessionStatus);
    }

    private JsonNode postForm(String path, MultiValueMap<String, String> form) {
        HttpHeaders headers = buildStripeHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(form, headers);
        try {
            ResponseEntity<String> response = restTemplate().exchange(
                    STRIPE_API_BASE_URL + path,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            return parseJson(response.getBody());
        } catch (HttpStatusCodeException ex) {
            throw new BusinessRuleException("Stripe API error: " + extractStripeErrorMessage(ex.getResponseBodyAsString()));
        } catch (ResourceAccessException ex) {
            throw new BusinessRuleException("Stripe API is currently unreachable.");
        }
    }

    private JsonNode get(String path) {
        HttpEntity<Void> requestEntity = new HttpEntity<>(buildStripeHeaders());
        try {
            ResponseEntity<String> response = restTemplate().exchange(
                    STRIPE_API_BASE_URL + path,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );
            return parseJson(response.getBody());
        } catch (HttpStatusCodeException ex) {
            throw new BusinessRuleException("Stripe API error: " + extractStripeErrorMessage(ex.getResponseBodyAsString()));
        } catch (ResourceAccessException ex) {
            throw new BusinessRuleException("Stripe API is currently unreachable.");
        }
    }

    private HttpHeaders buildStripeHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(stripeSecretKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
        return headers;
    }

    private JsonNode parseJson(String body) {
        if (isBlank(body)) {
            throw new BusinessRuleException("Stripe returned an empty response.");
        }

        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new BusinessRuleException("Unable to parse Stripe response.");
        }
    }

    private String extractStripeErrorMessage(String body) {
        try {
            JsonNode node = parseJson(body);
            String message = node.path("error").path("message").asText("");
            if (!isBlank(message)) {
                return message;
            }
        } catch (Exception ignored) {
        }
        return "Unexpected Stripe error.";
    }

    private long toMinorUnit(Double amount) {
        if (amount == null || amount < 0) {
            throw new BusinessRuleException("Invalid cart item amount for Stripe checkout.");
        }
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String safeProductName(String title) {
        if (isBlank(title)) {
            return "Learning Pack";
        }
        return title.length() > 120 ? title.substring(0, 120) : title;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private RestTemplate restTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
}
