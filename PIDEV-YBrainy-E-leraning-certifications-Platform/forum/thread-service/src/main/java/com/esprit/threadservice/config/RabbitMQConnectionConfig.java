package com.esprit.threadservice.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RabbitMQConnectionConfig {

    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username:guest}")
    private String username;

    @Value("${spring.rabbitmq.password:guest}")
    private String password;

    @Bean
    @Primary
    public CachingConnectionFactory rabbitConnectionFactory() {
        com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        factory.setConnectionTimeout(5000);

        CachingConnectionFactory cachingFactory = new CachingConnectionFactory(factory);
        cachingFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.NONE);
        return cachingFactory;
    }
}
