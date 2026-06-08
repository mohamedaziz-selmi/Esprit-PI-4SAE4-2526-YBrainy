package tn.esprit.eventservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(RecommendationMlProperties.class)
public class RecommendationMlConfig {

    @Bean
    public RestTemplate recommendationMlRestTemplate() {
        return new RestTemplate();
    }
}
