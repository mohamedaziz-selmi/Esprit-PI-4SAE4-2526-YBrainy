package tn.esprit.personalitybehaviorservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    @Bean
    public TopicExchange ybrainyEventsExchange(@Value("${app.rabbitmq.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange(@Value("${app.rabbitmq.dead-letter-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue personalityEventsQueue(
            @Value("${app.rabbitmq.personality-events-queue}") String queueName,
            @Value("${app.rabbitmq.dead-letter-exchange}") String deadLetterExchange,
            @Value("${app.rabbitmq.personality-events-dead-letter-routing-key}") String deadLetterRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", deadLetterRoutingKey)
                .build();
    }

    @Bean
    public Queue personalityEventsDeadLetterQueue(
            @Value("${app.rabbitmq.personality-events-dead-letter-queue}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Queue userDeletedQueue(
            @Value("${app.rabbitmq.user-deleted-queue}") String queueName,
            @Value("${app.rabbitmq.dead-letter-exchange}") String deadLetterExchange,
            @Value("${app.rabbitmq.user-deleted-dead-letter-routing-key}") String deadLetterRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", deadLetterRoutingKey)
                .build();
    }

    @Bean
    public Queue userDeletedDeadLetterQueue(
            @Value("${app.rabbitmq.user-deleted-dead-letter-queue}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding personalityEventsBinding(
            @Qualifier("personalityEventsQueue") Queue personalityEventsQueue,
            @Qualifier("ybrainyEventsExchange") TopicExchange ybrainyEventsExchange,
            @Value("${app.rabbitmq.personality-events-routing-key-pattern}") String routingKeyPattern) {
        return BindingBuilder.bind(personalityEventsQueue).to(ybrainyEventsExchange).with(routingKeyPattern);
    }

    @Bean
    public Binding personalityEventsDeadLetterBinding(
            @Qualifier("personalityEventsDeadLetterQueue") Queue personalityEventsDeadLetterQueue,
            @Qualifier("deadLetterExchange") TopicExchange deadLetterExchange,
            @Value("${app.rabbitmq.personality-events-dead-letter-routing-key}") String routingKey) {
        return BindingBuilder.bind(personalityEventsDeadLetterQueue).to(deadLetterExchange).with(routingKey);
    }

    @Bean
    public Binding userDeletedBinding(
            @Qualifier("userDeletedQueue") Queue userDeletedQueue,
            @Qualifier("ybrainyEventsExchange") TopicExchange ybrainyEventsExchange,
            @Value("${app.rabbitmq.user-deleted-routing-key}") String routingKey) {
        return BindingBuilder.bind(userDeletedQueue).to(ybrainyEventsExchange).with(routingKey);
    }

    @Bean
    public Binding userDeletedDeadLetterBinding(
            @Qualifier("userDeletedDeadLetterQueue") Queue userDeletedDeadLetterQueue,
            @Qualifier("deadLetterExchange") TopicExchange deadLetterExchange,
            @Value("${app.rabbitmq.user-deleted-dead-letter-routing-key}") String routingKey) {
        return BindingBuilder.bind(userDeletedDeadLetterQueue).to(deadLetterExchange).with(routingKey);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public RetryOperationsInterceptor rabbitRetryInterceptor(
            @Value("${app.rabbitmq.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.rabbitmq.retry.initial-interval-ms:1000}") long initialInterval,
            @Value("${app.rabbitmq.retry.multiplier:2.0}") double multiplier,
            @Value("${app.rabbitmq.retry.max-interval-ms:10000}") long maxInterval) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(initialInterval, multiplier, maxInterval)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter,
            RetryOperationsInterceptor rabbitRetryInterceptor,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        factory.setAdviceChain(rabbitRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
