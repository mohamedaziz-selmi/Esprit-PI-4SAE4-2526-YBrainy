package com.ybrainy.finance.messaging;

import com.backend.events.PaymentCompletedEvent;
import com.ybrainy.finance.service.FinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedConsumer {

    private final FinanceService financeService;

    @RabbitListener(queues = "${app.rabbitmq.finance-payment-queue}")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received payment.completed event for cart {}", event.getCartId());
        financeService.recordPaymentIncome(event);
    }
}