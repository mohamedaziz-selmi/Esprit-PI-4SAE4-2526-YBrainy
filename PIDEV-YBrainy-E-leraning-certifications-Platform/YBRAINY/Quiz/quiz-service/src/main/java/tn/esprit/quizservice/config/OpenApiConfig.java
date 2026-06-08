package tn.esprit.quizservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("YBrainy Quiz Service API")
                .version("1.0.0")
                .description("Manages quizzes, questions, submissions and leaderboards"));
    }
}
