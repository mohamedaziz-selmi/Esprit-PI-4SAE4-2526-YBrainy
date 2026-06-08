package tn.esprit.apigateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strips any client-supplied X-User-Id header (prevent spoofing), then for
 * authenticated requests resolves the Keycloak subject (UUID) to the internal
 * numeric userId via breadandbutteruser and re-adds the header so downstream
 * services can trust it without performing their own JWT validation.
 */
@Component
public class UserIdPropagationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(UserIdPropagationFilter.class);

    private final WebClient webClient;
    private final Map<String, Long> keycloakToUserIdCache = new ConcurrentHashMap<>();

    public UserIdPropagationFilter(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Always strip any client-supplied header first to prevent injection
        ServerWebExchange sanitized = exchange.mutate()
                .request(r -> r.headers(h -> h.remove("X-User-Id")))
                .build();

        return sanitized.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(token -> {
                    String keycloakId = token.getToken().getSubject();
                    if (keycloakId == null || keycloakId.isBlank()) {
                        return Mono.just(sanitized);
                    }
                    Long cached = keycloakToUserIdCache.get(keycloakId);
                    if (cached != null) {
                        return Mono.just(addUserId(sanitized, cached));
                    }
                    return resolveUserId(keycloakId)
                            .map(userId -> {
                                if (userId > 0) keycloakToUserIdCache.put(keycloakId, userId);
                                return addUserId(sanitized, userId);
                            })
                            .onErrorResume(e -> {
                                log.warn("Could not resolve keycloakId {} to userId: {}", keycloakId, e.getMessage());
                                return Mono.just(sanitized);
                            });
                })
                .defaultIfEmpty(sanitized)
                .flatMap(chain::filter);
    }

    private Mono<Long> resolveUserId(String keycloakId) {
        return webClient.get()
                .uri("lb://breadandbutteruser/api/users/internal/by-keycloak/" + keycloakId)
                .retrieve()
                .bodyToMono(InternalUserDto.class)
                .map(dto -> dto.userId() != null ? dto.userId() : 0L);
    }

    private ServerWebExchange addUserId(ServerWebExchange exchange, long userId) {
        if (userId <= 0) return exchange;
        return exchange.mutate()
                .request(r -> r.headers(h -> h.set("X-User-Id", String.valueOf(userId))))
                .build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private record InternalUserDto(Long userId, String username, String email,
                                   String firstName, String lastName) {}
}
