package com.backend.messaging;

import com.backend.events.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.payment-completed-routing-key}")
    private String paymentCompletedRoutingKey;

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        rabbitTemplate.convertAndSend(exchange, paymentCompletedRoutingKey, event);
        log.info("Published payment.completed event for cart {}", event.getCartId());
    }
}