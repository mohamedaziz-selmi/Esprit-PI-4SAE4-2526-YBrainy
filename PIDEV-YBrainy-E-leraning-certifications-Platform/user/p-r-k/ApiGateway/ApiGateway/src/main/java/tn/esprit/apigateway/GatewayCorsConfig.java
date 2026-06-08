package tn.esprit.apigateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class GatewayCorsConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 1. Allow origins from env var (ALLOWED_ORIGINS=comma-separated list)
        String originsEnv = System.getenv().getOrDefault("ALLOWED_ORIGINS", "http://localhost:4200,http://localhost:4201");
        config.setAllowedOrigins(Arrays.asList(originsEnv.split(",")));

        // 2. Allow common HTTP methods
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 3. Allow necessary headers (like Content-Type for JSON)
        config.setAllowedHeaders(Collections.singletonList("*"));

        // 4. Allow credentials if you plan to use cookies/sessions later
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply this configuration to all paths
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}