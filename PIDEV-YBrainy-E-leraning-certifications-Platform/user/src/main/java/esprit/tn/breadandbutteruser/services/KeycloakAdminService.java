package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.config.KeycloakAdminProperties;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminService {
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String INVALID_CLIENT_CONFIG_MESSAGE = "Keycloak auth client configuration is invalid (client id/secret)";
    private static final String DIRECT_ACCESS_GRANTS_MESSAGE =
            "Keycloak auth client is not allowed to use password login (enable Direct Access Grants)";
    private static final String SESSION_EXPIRED_MESSAGE = "Session expired. Please sign in again.";

    private final WebClient webClient;
    private final KeycloakAdminProperties props;
    private final Object serviceTokenLock = new Object();
    private volatile String cachedServiceAccountAccessToken;
    private volatile Instant cachedServiceAccountTokenExpiresAt = Instant.EPOCH;

    public String getServiceAccountAccessToken() {
        Instant now = Instant.now();
        if (cachedServiceAccountAccessToken != null && now.plusSeconds(30).isBefore(cachedServiceAccountTokenExpiresAt)) {
            return cachedServiceAccountAccessToken;
        }

        synchronized (serviceTokenLock) {
            now = Instant.now();
            if (cachedServiceAccountAccessToken != null && now.plusSeconds(30).isBefore(cachedServiceAccountTokenExpiresAt)) {
                return cachedServiceAccountAccessToken;
            }

            Map<String, Object> resp = requestAdminAccessTokenWithFallback();
            cacheServiceToken(resp);
            return cachedServiceAccountAccessToken;
        }
    }

    public Map<String, Object> loginUser(String username, String password) {
        boolean authClientConfigured = StringUtils.hasText(props.getAuth().getClientId());
        String authClientId = authClientConfigured
                ? props.getAuth().getClientId()
                : props.getAdmin().getClientId();
        String authClientSecret = StringUtils.hasText(props.getAuth().getClientSecret())
                ? props.getAuth().getClientSecret()
                : null;

        if (!StringUtils.hasText(authClientId)) {
            throw new RuntimeException("Keycloak auth client ID is not configured");
        }

        try {
            return requestUserPasswordGrantWithFallback(authClientId, authClientSecret, authClientConfigured, username, password);
        } catch (RuntimeException firstFailure) {
            if (shouldRetryWithResolvedUsername(firstFailure) && StringUtils.hasText(username) && username.contains("@")) {
                Optional<String> resolvedUsername = findUsernameByEmail(username);
                if (resolvedUsername.isPresent() && !username.equalsIgnoreCase(resolvedUsername.get())) {
                    return requestUserPasswordGrantWithFallback(authClientId, authClientSecret, authClientConfigured,
                            resolvedUsername.get(), password);
                }
            }
            throw firstFailure;
        }
    }

    public Map<String, Object> refreshUserSession(String refreshToken) {
        boolean authClientConfigured = StringUtils.hasText(props.getAuth().getClientId());
        String authClientId = authClientConfigured
                ? props.getAuth().getClientId()
                : props.getAdmin().getClientId();
        String authClientSecret = StringUtils.hasText(props.getAuth().getClientSecret())
                ? props.getAuth().getClientSecret()
                : null;

        if (!StringUtils.hasText(authClientId)) {
            throw new RuntimeException("Keycloak auth client ID is not configured");
        }
        if (!StringUtils.hasText(refreshToken)) {
            throw new RuntimeException("Refresh token is required");
        }

        return requestUserRefreshGrantWithFallback(authClientId, authClientSecret, authClientConfigured, refreshToken);
    }

    public String createUser(String username, String email, String firstName, String lastName, boolean enabled) {
        String token = getServiceAccountAccessToken();

        String url = usersBaseUrl();

        Map<String, Object> payload = Map.of(
                "username", username,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", enabled,
                "emailVerified", true
        );

        ResponseEntity<Void> response = webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchangeToMono(resp -> {
                    if (resp.statusCode().is2xxSuccessful()) {
                        return resp.toBodilessEntity();
                    }
                    return resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                return reactor.core.publisher.Mono.error(
                                        new RuntimeException("Keycloak create user failed: HTTP " + resp.statusCode().value() + " Body: " + body)
                                );
                            });
                })
                .block();

        Optional<String> createdUserId = extractUserIdFromLocation(response != null ? response.getHeaders().getLocation() : null);
        if (createdUserId.isPresent()) {
            return createdUserId.get();
        }

        // Fallback for deployments that omit Location or where proxies rewrite response headers.
        // Realms with "Email as username" enabled can normalize the stored username to the email value.
        return findUserIdByUsername(token, username)
                .or(() -> findUserIdByEmail(token, email))
                .orElseThrow(() -> new RuntimeException("Keycloak user created but unable to resolve user id"));
    }

    public void setPassword(String keycloakUserId, String rawPassword, boolean temporary) {
        String token = getServiceAccountAccessToken();
        String url = props.getBaseUrl() + "/admin/realms/" + props.getRealm() + "/users/" + keycloakUserId + "/reset-password";

        Map<String, Object> payload = Map.of(
                "type", "password",
                "temporary", temporary,
                "value", rawPassword
        );

        webClient.put()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public void assignRealmRole(String keycloakUserId, Role role) {
        String token = getServiceAccountAccessToken();

        String roleUrl = props.getBaseUrl() + "/admin/realms/" + props.getRealm() + "/roles/" + role.name();
        Map<String, Object> roleRep = webClient.get()
                .uri(roleUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (roleRep == null) {
            throw new RuntimeException("Unable to resolve realm role in Keycloak: " + role.name());
        }

        String mappingUrl = props.getBaseUrl() + "/admin/realms/" + props.getRealm() + "/users/" + keycloakUserId + "/role-mappings/realm";

        webClient.post()
                .uri(mappingUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(roleRep))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public void setCompany(String keycloakUserId, String company) {
        updateSingleAttribute(keycloakUserId, "company", company);
    }

    public void setApproved(String keycloakUserId, boolean approved) {
        updateSingleAttribute(keycloakUserId, "approved", String.valueOf(approved));
    }

    public void setBanned(String keycloakUserId, boolean banned) {
        updateUserRepresentation(keycloakUserId, payload -> payload.put("enabled", !banned));
    }

    public void setFaceBiometricHash(String keycloakUserId, String faceBiometricHash) {
        updateUserRepresentation(keycloakUserId, payload -> {
            Map<String, Object> attributes = copyAttributes(payload.get("attributes"));
            if (StringUtils.hasText(faceBiometricHash)) {
                attributes.put("faceBiometricHash", List.of(faceBiometricHash));
            } else {
                attributes.remove("faceBiometricHash");
            }
            payload.put("attributes", attributes);
        });
    }

    public ImpersonationSession startUserImpersonationSession(String keycloakUserId) {
        String token = getServiceAccountAccessToken();
        String url = props.getBaseUrl() + "/admin/realms/" + props.getRealm() + "/users/" + keycloakUserId + "/impersonation";

        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchangeToMono(response -> {
                    List<String> setCookies = response.headers().header(HttpHeaders.SET_COOKIE);
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(Map.class)
                                .defaultIfEmpty(Map.of())
                                .map(body -> new ImpersonationSession(
                                        setCookies,
                                        body.get("redirect") != null ? String.valueOf(body.get("redirect")) : null
                                ));
                    }

                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> reactor.core.publisher.Mono.error(
                                    new RuntimeException("Keycloak impersonation failed: HTTP "
                                            + response.statusCode().value() + " Body: " + body)
                            ));
                })
                .blockOptional()
                .filter(session -> session.setCookieHeaders() != null && !session.setCookieHeaders().isEmpty())
                .orElseThrow(() -> new RuntimeException("Keycloak impersonation did not return a browser session."));
    }

    public List<Map<String, Object>> listUsers(String search) {
        return listUsers(search, null, null);
    }

    public List<Map<String, Object>> listUsers(String search, Integer first, Integer max) {
        String token = getServiceAccountAccessToken();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(usersBaseUrl());
        if (search != null && !search.isBlank()) {
            uriBuilder.queryParam("search", search);
        }
        if (first != null && first >= 0) {
            uriBuilder.queryParam("first", first);
        }
        if (max != null && max > 0) {
            uriBuilder.queryParam("max", max);
        }
        String url = uriBuilder.build(true).toUriString();

        List<Map<String, Object>> users = webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        return users != null ? users : List.of();
    }

    public Optional<Boolean> isUserEnabled(String keycloakUserId) {
        if (!StringUtils.hasText(keycloakUserId)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> representation = getUserRepresentation(keycloakUserId, getServiceAccountAccessToken());
            if (representation == null) {
                return Optional.empty();
            }
            Object enabled = representation.get("enabled");
            if (enabled instanceof Boolean bool) {
                return Optional.of(bool);
            }
            if (enabled != null) {
                return Optional.of(Boolean.parseBoolean(String.valueOf(enabled)));
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Failed to read Keycloak enabled flag for user {}: {}", keycloakUserId, ex.getMessage());
            return Optional.empty();
        }
    }

    public void setEnabled(String keycloakUserId, boolean enabled) {
        updateUserRepresentation(keycloakUserId, payload -> payload.put("enabled", enabled));
    }

    public void updateRole(String keycloakUserId, Role newRole) {
        String token = getServiceAccountAccessToken();

        // Fetch current realm role mappings
        String mappingUrl = props.getBaseUrl() + "/admin/realms/" + props.getRealm() + "/users/" + keycloakUserId + "/role-mappings/realm";
        List<Map<String, Object>> currentRoles = webClient.get()
                .uri(mappingUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (currentRoles != null) {
            // Remove all existing realm roles
            webClient.method(HttpMethod.DELETE)
                    .uri(mappingUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(currentRoles)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        }

        // Assign new role
        assignRealmRole(keycloakUserId, newRole);
    }

    public Optional<String> findUserIdByUsernameOrEmail(String username, String email) {
        if (StringUtils.hasText(username)) {
            Optional<String> byUsername = findUserIdByUsername(username);
            if (byUsername.isPresent()) {
                return byUsername;
            }
        }
        if (StringUtils.hasText(email)) {
            return findUserIdByEmail(email);
        }
        return Optional.empty();
    }

    public Optional<String> findUserIdByUsername(String username) {
        return findUserIdByUsername(getServiceAccountAccessToken(), username);
    }

    public Optional<String> findUserIdByEmail(String email) {
        return findUserIdByEmail(getServiceAccountAccessToken(), email);
    }

    public void deleteUser(String keycloakUserId) {
        webClient.delete()
                .uri(userUrl(keycloakUserId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getServiceAccountAccessToken())
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public void updateUserProfile(String keycloakUserId, String username, String email, String firstName, String lastName) {
        updateUserRepresentation(keycloakUserId, payload -> {
            payload.put("username", username);
            payload.put("email", email);
            payload.put("firstName", firstName);
            payload.put("lastName", lastName);
        });
    }

    public void sendUpdatePasswordEmail(String keycloakUserId) {
        String url = userUrl(keycloakUserId) + "/execute-actions-email";
        webClient.put()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getServiceAccountAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of("UPDATE_PASSWORD"))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private Optional<String> findUserIdByUsername(String token, String username) {
        String url = UriComponentsBuilder.fromHttpUrl(usersBaseUrl())
                .queryParam("username", username)
                .queryParam("exact", true)
                .build(true)
                .toUriString();

        List<Map<String, Object>> users = webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (users == null) {
            return Optional.empty();
        }

        return users.stream()
                .filter(u -> username.equalsIgnoreCase(String.valueOf(u.get("username"))))
                .map(u -> u.get("id"))
                .filter(id -> id != null)
                .map(String::valueOf)
                .findFirst();
    }

    private Optional<String> findUsernameByEmail(String email) {
        String token = getServiceAccountAccessToken();
        String url = UriComponentsBuilder.fromHttpUrl(usersBaseUrl())
                .queryParam("email", email)
                .queryParam("exact", true)
                .build(true)
                .toUriString();

        List<Map<String, Object>> users = webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (users == null) {
            return Optional.empty();
        }

        return users.stream()
                .filter(u -> email.equalsIgnoreCase(String.valueOf(u.get("email"))))
                .map(u -> u.get("username"))
                .filter(u -> u != null && StringUtils.hasText(String.valueOf(u)))
                .map(String::valueOf)
                .findFirst();
    }

    private Optional<String> findUserIdByEmail(String token, String email) {
        String url = UriComponentsBuilder.fromHttpUrl(usersBaseUrl())
                .queryParam("email", email)
                .queryParam("exact", true)
                .build(true)
                .toUriString();

        List<Map<String, Object>> users = webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (users == null) {
            return Optional.empty();
        }

        return users.stream()
                .filter(u -> email.equalsIgnoreCase(String.valueOf(u.get("email"))))
                .map(u -> u.get("id"))
                .filter(id -> id != null)
                .map(String::valueOf)
                .findFirst();
    }

    private void updateSingleAttribute(String keycloakUserId, String attributeName, String attributeValue) {
        updateUserRepresentation(keycloakUserId, payload -> {
            Map<String, Object> attributes = copyAttributes(payload.get("attributes"));
            attributes.put(attributeName, List.of(attributeValue));
            payload.put("attributes", attributes);
        });
    }

    private Map<String, Object> copyAttributes(Object existingAttributes) {
        Map<String, Object> attributes = new HashMap<>();
        if (existingAttributes instanceof Map<?, ?> existingMap) {
            for (Map.Entry<?, ?> entry : existingMap.entrySet()) {
                if (entry.getKey() != null) {
                    attributes.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return attributes;
    }

    private void updateUserRepresentation(String keycloakUserId, Consumer<Map<String, Object>> mutator) {
        String token = getServiceAccountAccessToken();
        Map<String, Object> currentUser = getUserRepresentation(keycloakUserId, token);
        Map<String, Object> payload = currentUser != null ? new HashMap<>(currentUser) : new HashMap<>();

        // Read-only/admin metadata returned by GET and rejected by PUT on some Keycloak versions.
        payload.remove("access");

        mutator.accept(payload);

        webClient.put()
                .uri(userUrl(keycloakUserId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private Map<String, Object> getUserRepresentation(String keycloakUserId, String token) {
        return webClient.get()
                .uri(userUrl(keycloakUserId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    private String tokenEndpointUrl() {
        return tokenEndpointUrl(props.getRealm());
    }

    private String tokenEndpointUrl(String realm) {
        return props.getBaseUrl() + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    private String usersBaseUrl() {
        return props.getBaseUrl() + "/admin/realms/" + props.getRealm() + "/users";
    }

    private String userUrl(String keycloakUserId) {
        return usersBaseUrl() + "/" + keycloakUserId;
    }

    private Optional<String> extractUserIdFromLocation(URI location) {
        if (location == null) {
            return Optional.empty();
        }

        String path = location.getPath();
        if (!StringUtils.hasText(path)) {
            return Optional.empty();
        }

        String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = normalizedPath.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == normalizedPath.length() - 1) {
            return Optional.empty();
        }

        String userId = normalizedPath.substring(lastSlash + 1).trim();
        return StringUtils.hasText(userId) ? Optional.of(userId) : Optional.empty();
    }

    private long parseLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Map<String, Object> requestAdminAccessTokenWithFallback() {
        try {
            return requestAdminTokenWithClientCredentials();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() != 401) {
                throw new RuntimeException("Unable to obtain Keycloak admin access token: HTTP "
                        + ex.getStatusCode().value() + " Body: " + safeBody(ex));
            }
            return requestAdminTokenWithPasswordGrant(ex);
        } catch (RuntimeException ex) {
            // If client-credentials setup is incomplete locally, try admin-cli password grant fallback.
            return requestAdminTokenWithPasswordGrant(ex);
        }
    }

    private Map<String, Object> requestAdminTokenWithClientCredentials() {
        if (!StringUtils.hasText(props.getAdmin().getClientId())) {
            throw new RuntimeException("Keycloak admin client ID is not configured");
        }
        if (!StringUtils.hasText(props.getAdmin().getClientSecret())) {
            throw new RuntimeException("Keycloak admin client secret is not configured");
        }

        return webClient
                .post()
                .uri(tokenEndpointUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("client_id", props.getAdmin().getClientId())
                        .with("client_secret", props.getAdmin().getClientSecret()))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    private Map<String, Object> requestAdminTokenWithPasswordGrant(Throwable originalFailure) {
        String username = props.getAdmin().getUsername();
        String password = props.getAdmin().getPassword();
        String adminRealm = StringUtils.hasText(props.getAdmin().getRealm()) ? props.getAdmin().getRealm() : "master";
        String adminClientId = StringUtils.hasText(props.getAdmin().getFallbackClientId())
                ? props.getAdmin().getFallbackClientId()
                : "admin-cli";

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new RuntimeException("Unable to obtain Keycloak admin access token. "
                    + "Client-credentials auth failed and admin username/password fallback is not configured.", originalFailure);
        }

        try {
            Map<String, Object> resp = webClient
                    .post()
                    .uri(tokenEndpointUrl(adminRealm))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "password")
                            .with("client_id", adminClientId)
                            .with("username", username)
                            .with("password", password))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null || resp.get("access_token") == null) {
                throw new RuntimeException("Keycloak admin fallback login returned no token");
            }

            return resp;
        } catch (WebClientResponseException ex) {
            throw new RuntimeException("Unable to obtain Keycloak admin access token. "
                    + "Client-credentials auth failed and fallback admin-cli login also failed: HTTP "
                    + ex.getStatusCode().value() + " Body: " + safeBody(ex), ex);
        }
    }

    private void cacheServiceToken(Map<String, Object> resp) {
        if (resp == null || resp.get("access_token") == null) {
            throw new RuntimeException("Unable to obtain Keycloak admin access token");
        }
        cachedServiceAccountAccessToken = String.valueOf(resp.get("access_token"));
        cachedServiceAccountTokenExpiresAt = Instant.now()
                .plusSeconds(Math.max(parseLong(resp.get("expires_in"), 60L), 30L));
    }

    private String safeBody(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body != null ? body : "";
    }

    private Map<String, Object> requestUserPasswordGrantWithFallback(String authClientId,
                                                                     String authClientSecret,
                                                                     boolean authClientConfigured,
                                                                     String username,
                                                                     String password) {
        try {
            return requestUserPasswordGrant(authClientId, authClientSecret, username, password);
        } catch (KeycloakLoginException ex) {
            if (ex.isInvalidClient() && StringUtils.hasText(authClientSecret)) {
                log.warn("Keycloak password login returned invalid_client for auth client '{}' in realm '{}'; retrying without client secret",
                        authClientId, props.getRealm());
                try {
                    return requestUserPasswordGrant(authClientId, null, username, password);
                } catch (RuntimeException retryFailure) {
                    throw enrichInvalidClientFailure(retryFailure, authClientId, true, authClientConfigured);
                }
            }
            throw enrichInvalidClientFailure(ex, authClientId, StringUtils.hasText(authClientSecret), authClientConfigured);
        }
    }

    private Map<String, Object> requestUserRefreshGrantWithFallback(String authClientId,
                                                                    String authClientSecret,
                                                                    boolean authClientConfigured,
                                                                    String refreshToken) {
        try {
            return requestUserRefreshGrant(authClientId, authClientSecret, refreshToken);
        } catch (KeycloakLoginException ex) {
            if (ex.isInvalidClient() && StringUtils.hasText(authClientSecret)) {
                log.warn("Keycloak refresh grant returned invalid_client for auth client '{}' in realm '{}'; retrying without client secret",
                        authClientId, props.getRealm());
                try {
                    return requestUserRefreshGrant(authClientId, null, refreshToken);
                } catch (RuntimeException retryFailure) {
                    throw mapRefreshGrantFailure(retryFailure, authClientId, true, authClientConfigured);
                }
            }
            throw mapRefreshGrantFailure(ex, authClientId, StringUtils.hasText(authClientSecret), authClientConfigured);
        }
    }

    private Map<String, Object> requestUserPasswordGrant(String authClientId,
                                                         String authClientSecret,
                                                         String username,
                                                         String password) {
        BodyInserters.FormInserter<String> formData = BodyInserters.fromFormData("grant_type", "password")
                .with("client_id", authClientId)
                .with("username", username)
                .with("password", password)
                .with("scope", "openid");

        if (StringUtils.hasText(authClientSecret)) {
            formData = formData.with("client_secret", authClientSecret);
        }

        Map<String, Object> resp = webClient
                .post()
                .uri(tokenEndpointUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(Map.class);
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> reactor.core.publisher.Mono.error(
                                    new KeycloakLoginException(response.statusCode().value(), body)
                            ));
                })
                .block();

        if (resp == null || resp.get("access_token") == null) {
            throw new RuntimeException(INVALID_CREDENTIALS_MESSAGE);
        }

        return resp;
    }

    private Map<String, Object> requestUserRefreshGrant(String authClientId,
                                                        String authClientSecret,
                                                        String refreshToken) {
        BodyInserters.FormInserter<String> formData = BodyInserters.fromFormData("grant_type", "refresh_token")
                .with("client_id", authClientId)
                .with("refresh_token", refreshToken)
                .with("scope", "openid");

        if (StringUtils.hasText(authClientSecret)) {
            formData = formData.with("client_secret", authClientSecret);
        }

        Map<String, Object> resp = webClient
                .post()
                .uri(tokenEndpointUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(Map.class);
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> reactor.core.publisher.Mono.error(
                                    new KeycloakLoginException(response.statusCode().value(), body)
                            ));
                })
                .block();

        if (resp == null || resp.get("access_token") == null) {
            throw new RuntimeException(SESSION_EXPIRED_MESSAGE);
        }

        return resp;
    }

    private boolean shouldRetryWithResolvedUsername(RuntimeException failure) {
        return INVALID_CREDENTIALS_MESSAGE.equals(failure.getMessage());
    }

    private RuntimeException enrichInvalidClientFailure(RuntimeException failure,
                                                       String authClientId,
                                                       boolean secretWasProvided,
                                                       boolean authClientConfigured) {
        if (!isInvalidClientFailure(failure)) {
            return failure;
        }

        StringBuilder message = new StringBuilder(INVALID_CLIENT_CONFIG_MESSAGE)
                .append(" [realm=")
                .append(props.getRealm())
                .append(", clientId=")
                .append(authClientId)
                .append(", clientSecretProvided=")
                .append(secretWasProvided)
                .append("]. Verify the client exists and matches Keycloak 'Client authentication' setting.");

        if (secretWasProvided) {
            message.append(" If this is a Public client, unset keycloak.auth.client-secret / KEYCLOAK_AUTH_CLIENT_SECRET.");
        } else {
            message.append(" If this is a Confidential client, set keycloak.auth.client-secret / KEYCLOAK_AUTH_CLIENT_SECRET.");
        }

        if (!authClientConfigured) {
            message.append(" keycloak.auth.client-id is not configured, so login is currently falling back to keycloak.admin.client-id.");
        }

        return new RuntimeException(message.toString(), failure);
    }

    private RuntimeException mapRefreshGrantFailure(RuntimeException failure,
                                                    String authClientId,
                                                    boolean secretWasProvided,
                                                    boolean authClientConfigured) {
        if (failure instanceof KeycloakLoginException keycloakLoginException && keycloakLoginException.isInvalidGrant()) {
            return new RuntimeException(SESSION_EXPIRED_MESSAGE, failure);
        }
        return enrichInvalidClientFailure(failure, authClientId, secretWasProvided, authClientConfigured);
    }

    private boolean isInvalidClientFailure(RuntimeException failure) {
        if (failure instanceof KeycloakLoginException keycloakLoginException) {
            return keycloakLoginException.isInvalidClient();
        }
        return INVALID_CLIENT_CONFIG_MESSAGE.equals(failure.getMessage());
    }

    static String mapKeycloakLoginError(int statusCode, String body) {
        String normalizedBody = body != null ? body.toLowerCase() : "";

        if (statusCode == 400 || statusCode == 401) {
            if (normalizedBody.contains("unauthorized_client")) {
                return DIRECT_ACCESS_GRANTS_MESSAGE;
            }
            if (normalizedBody.contains("invalid_client")) {
                return INVALID_CLIENT_CONFIG_MESSAGE;
            }
            if (normalizedBody.contains("account is disabled")
                    || normalizedBody.contains("account disabled")
                    || normalizedBody.contains("user_disabled")) {
                return "Account is currently restricted. Submit a ban appeal to request review.";
            }
            if (normalizedBody.contains("invalid_grant")) {
                return INVALID_CREDENTIALS_MESSAGE;
            }
            if (StringUtils.hasText(body)) {
                return "Keycloak login failed: HTTP " + statusCode + " Body: " + body;
            }
            return INVALID_CREDENTIALS_MESSAGE;
        }

        return "Keycloak login failed: HTTP " + statusCode + " Body: " + (body != null ? body : "");
    }

    private static final class KeycloakLoginException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;

        private KeycloakLoginException(int statusCode, String responseBody) {
            super(mapKeycloakLoginError(statusCode, responseBody));
            this.statusCode = statusCode;
            this.responseBody = responseBody != null ? responseBody : "";
        }

        private boolean isInvalidClient() {
            if (statusCode != 400 && statusCode != 401) {
                return false;
            }
            return responseBody.toLowerCase(Locale.ROOT).contains("invalid_client");
        }

        private boolean isInvalidGrant() {
            if (statusCode != 400 && statusCode != 401) {
                return false;
            }
            return responseBody.toLowerCase(Locale.ROOT).contains("invalid_grant");
        }
    }

    public record ImpersonationSession(List<String> setCookieHeaders, String redirectUrl) {
    }
}
