package esprit.tn.breadandbutteruser.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange ybrainyEventsExchange(@Value("${app.rabbitmq.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue userXpAwardQueue(@Value("${app.rabbitmq.user-xp-award-queue}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding userXpAwardBinding(
            Queue userXpAwardQueue,
            TopicExchange ybrainyEventsExchange,
            @Value("${app.rabbitmq.user-xp-award-routing-key}") String routingKey) {
        return BindingBuilder.bind(userXpAwardQueue).to(ybrainyEventsExchange).with(routingKey);
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
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
