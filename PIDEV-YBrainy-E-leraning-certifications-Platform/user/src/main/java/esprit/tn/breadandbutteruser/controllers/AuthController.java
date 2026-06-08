package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.AuthResponseDto;
import esprit.tn.breadandbutteruser.dto.SignInDto;
import esprit.tn.breadandbutteruser.dto.SignUpChallengeRequestDto;
import esprit.tn.breadandbutteruser.dto.SignUpChallengeResponseDto;
import esprit.tn.breadandbutteruser.dto.SignUpDto;
import esprit.tn.breadandbutteruser.services.SignUpChallengeService;
import esprit.tn.breadandbutteruser.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Authentication", description = "APIs for user authentication")
public class AuthController {

    private final UserService userService;
    private final SignUpChallengeService signUpChallengeService;

    @PostMapping("/signup/challenge")
    @Operation(summary = "Create a personalized signup challenge", description = "Generates a one-time challenge token with a recommended challenge mode based on the user's current profile fields.")
    public ResponseEntity<SignUpChallengeResponseDto> createSignUpChallenge(
            @RequestBody(required = false) SignUpChallengeRequestDto request
    ) {
        SignUpChallengeRequestDto safeRequest = request != null
                ? request
                : new SignUpChallengeRequestDto(null, null, null);
        return ResponseEntity.ok(signUpChallengeService.createChallenge(safeRequest));
    }

    @PostMapping("/signup")
    @Operation(summary = "Sign up a new user", description = "Creates a new user account")
    public ResponseEntity<AuthResponseDto> signUp(@Valid @RequestBody SignUpDto signUpDto) {
        log.info("REST request to sign up user: {}", signUpDto.getUsername());
        AuthResponseDto result = userService.signUp(signUpDto);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    @PostMapping("/signin")
    @Operation(summary = "Sign in user", description = "Authenticates a user and returns a token")
    public ResponseEntity<AuthResponseDto> signIn(@Valid @RequestBody SignInDto signInDto) {
        log.info("REST request to sign in user: {}", signInDto.getEmail());
        AuthResponseDto result = userService.signIn(signInDto);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login")
    @Operation(summary = "Login (alias)", description = "Alias endpoint for /signin")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody SignInDto signInDto) {
        log.info("REST request to login user: {}", signInDto.getEmail());
        AuthResponseDto result = userService.signIn(signInDto);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/face/signin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sign in with enrolled face biometric", description = "Matches an uploaded camera frame, establishes a Keycloak browser session, and lets the SPA finish login through PKCE.")
    public ResponseEntity<Void> faceSignIn(@RequestPart("image") MultipartFile image) {
        log.info("REST request to sign in user with face biometric");
        UserService.FaceSignInSession session = userService.startFaceSignIn(image);

        HttpHeaders headers = new HttpHeaders();
        session.setCookieHeaders().forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, rewriteCookieDomain(cookie)));
        return ResponseEntity.noContent().headers(headers).build();
    }

    /**
     * Rewrite Set-Cookie attributes so Keycloak's session cookies actually reach
     * the browser on plain http://localhost dev/docker setups:
     *   1) Domain: backend talks to Keycloak via host.docker.internal:9190, so
     *      cookies come back tagged with that host. Browser hits Keycloak at
     *      localhost:9190, so we rewrite Domain=localhost to share the session
     *      across Angular (4200) and Keycloak (9190).
     *   2) Secure: Keycloak marks identity/session cookies Secure. The browser
     *      drops Secure cookies over HTTP, so Keycloak never sees the session.
     *      We strip the flag for development.
     *   3) SameSite=None: only honored together with Secure. Once we drop Secure
     *      we must downgrade SameSite=None to SameSite=Lax or browsers reject
     *      the whole Set-Cookie.
     * Override with APP_FACE_BIOMETRIC_COOKIE_DOMAIN for a real FQDN deployment.
     */
    private String rewriteCookieDomain(String setCookieHeader) {
        if (setCookieHeader == null || setCookieHeader.isBlank()) {
            return setCookieHeader;
        }
        String targetDomain = System.getenv().getOrDefault("APP_FACE_BIOMETRIC_COOKIE_DOMAIN", "localhost");
        String rewritten = setCookieHeader
                .replaceAll("(?i);\\s*Domain=[^;]+", "")
                .replaceAll("(?i);\\s*Secure", "")
                .replaceAll("(?i);\\s*SameSite=None", "; SameSite=Lax");
        return rewritten + "; Domain=" + targetDomain;
    }

    public record RefreshTokenRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken
    ) {}

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Uses a refresh token to obtain a new access token")
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request to refresh access token");
        AuthResponseDto result = userService.refreshSession(request.refreshToken());
        return ResponseEntity.ok(result);
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email should be valid")
            String email
    ) {}

    public record ForgotPasswordResetWithCodeRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email should be valid")
            String email,
            @NotBlank(message = "Verification code is required")
            String code,
            @NotBlank(message = "New password is required")
            String newPassword,
            @NotBlank(message = "Password confirmation is required")
            String confirmPassword
    ) {}

    public record ForgotPasswordResponse(
            boolean accepted,
            String delivery,
            String devFallbackCode
    ) {}

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Sends a verification code email if the account exists")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("REST request to forgot-password for email: {}", request.email());
        UserService.ForgotPasswordResult result = userService.forgotPassword(request.email());
        String delivery = result.devFallbackUsed() ? "DEV_FALLBACK" : "EMAIL_OR_IGNORED";
        return ResponseEntity.ok(new ForgotPasswordResponse(
                result.accepted(),
                delivery,
                result.devFallbackCode()
        ));
    }

    @PostMapping("/forgot-password/reset-with-code")
    @Operation(summary = "Reset password using verification code", description = "Verifies the forgot-password code and sets a new password")
    public ResponseEntity<Void> resetPasswordWithCode(@Valid @RequestBody ForgotPasswordResetWithCodeRequest request) {
        log.info("REST request to reset password with verification code for email: {}", request.email());
        userService.resetPasswordWithVerificationCode(
                request.email(),
                request.code(),
                request.newPassword(),
                request.confirmPassword()
        );
        return ResponseEntity.ok().build();
    }
}
