package esprit.tn.breadandbutteruser.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import esprit.tn.breadandbutteruser.dto.*;
import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import esprit.tn.breadandbutteruser.messaging.UserEventPublisher;
import esprit.tn.breadandbutteruser.repositories.BanAppealRepository;
import esprit.tn.breadandbutteruser.repositories.InteractionEventRepository;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import esprit.tn.breadandbutteruser.repositories.WarningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceCoreTest {

    private UserRepository userRepository;
    private WarningRepository warningRepository;
    private BanAppealRepository banAppealRepository;
    private InteractionEventRepository interactionEventRepository;
    private KeycloakAdminService keycloakAdminService;
    private SignUpChallengeService signUpChallengeService;
    private FaceBiometricEngineService faceBiometricEngineService;
    private ForgotPasswordVerificationService forgotPasswordVerificationService;
    private UserEventPublisher userEventPublisher;
    private ObjectMapper objectMapper;
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        warningRepository = mock(WarningRepository.class);
        banAppealRepository = mock(BanAppealRepository.class);
        interactionEventRepository = mock(InteractionEventRepository.class);
        keycloakAdminService = mock(KeycloakAdminService.class);
        signUpChallengeService = mock(SignUpChallengeService.class);
        faceBiometricEngineService = mock(FaceBiometricEngineService.class);
        forgotPasswordVerificationService = mock(ForgotPasswordVerificationService.class);
        userEventPublisher = mock(UserEventPublisher.class);
        objectMapper = new ObjectMapper();

        userService = new UserService(
                userRepository,
                warningRepository,
                banAppealRepository,
                interactionEventRepository,
                keycloakAdminService,
                signUpChallengeService,
                faceBiometricEngineService,
                forgotPasswordVerificationService,
                userEventPublisher,
                objectMapper
        );

        sampleUser = User.builder()
                .userId(1L)
                .keycloakUserId("kc-123")
                .username("johndoe")
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .role(Role.STUDENT)
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .age(25)
                .status(IntegrityStatus.SECURE)
                .accountCreatedAt(LocalDateTime.now())
                .lastProfileUpdate(LocalDateTime.now())
                .build();
    }

    // ── signUp ───────────────────────────────────────────────────────────────

    @Test
    void signUp_passwordMismatch_throwsException() {
        SignUpDto dto = SignUpDto.builder()
                .username("user1").email("user1@test.com")
                .password("Password1").confirmPassword("Password2")
                .role(Role.STUDENT).build();

        assertThatThrownBy(() -> userService.signUp(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Passwords do not match");
    }

    @Test
    void signUp_passwordTooShort_throwsException() {
        SignUpDto dto = SignUpDto.builder()
                .username("user1").email("user1@test.com")
                .password("Pass1").confirmPassword("Pass1")
                .role(Role.STUDENT).build();

        assertThatThrownBy(() -> userService.signUp(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Password must be at least 8 characters");
    }

    @Test
    void signUp_duplicateUsername_throwsException() {
        when(userRepository.existsByUsername("johndoe")).thenReturn(true);
        SignUpDto dto = SignUpDto.builder()
                .username("johndoe").email("new@test.com")
                .password("Password1").confirmPassword("Password1")
                .role(Role.STUDENT).build();

        assertThatThrownBy(() -> userService.signUp(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Username already exists");
    }

    @Test
    void signUp_duplicateEmail_throwsException() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("john@test.com")).thenReturn(true);
        SignUpDto dto = SignUpDto.builder()
                .username("newuser").email("john@test.com")
                .password("Password1").confirmPassword("Password1")
                .role(Role.STUDENT).build();

        assertThatThrownBy(() -> userService.signUp(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email already exists");
    }

    @Test
    void signUp_enterpriseUserWithoutCompanyName_throwsException() {
        SignUpDto dto = SignUpDto.builder()
                .username("ent1").email("ent1@test.com")
                .password("Password1").confirmPassword("Password1")
                .role(Role.ENTERPRISE_USER).companyName("  ").build();

        assertThatThrownBy(() -> userService.signUp(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("companyName is required for ENTERPRISE_USER");
    }

    @Test
    void signUp_success_returnsAuthResponse() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        doNothing().when(signUpChallengeService).validateChallenge(any(), any(), any());
        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("kc-new-id");
        doNothing().when(keycloakAdminService).setPassword(anyString(), anyString(), anyBoolean());
        doNothing().when(keycloakAdminService).assignRealmRole(anyString(), any());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setUserId(10L);
            return u;
        });

        SignUpDto dto = SignUpDto.builder()
                .username("newuser").email("new@test.com")
                .firstName("New").lastName("User")
                .password("Password1").confirmPassword("Password1")
                .role(Role.STUDENT).build();

        AuthResponseDto result = userService.signUp(dto);

        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getRole()).isEqualTo("STUDENT");
    }

    @Test
    void signUp_keycloakFailure_rollsBackKeycloakUser() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        doNothing().when(signUpChallengeService).validateChallenge(any(), any(), any());
        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("kc-fail-id");
        doThrow(new RuntimeException("Keycloak password set failed"))
                .when(keycloakAdminService).setPassword(anyString(), anyString(), anyBoolean());
        doNothing().when(keycloakAdminService).deleteUser("kc-fail-id");

        SignUpDto dto = SignUpDto.builder()
                .username("newuser").email("new@test.com")
                .firstName("New").lastName("User")
                .password("Password1").confirmPassword("Password1")
                .role(Role.STUDENT).build();

        assertThatThrownBy(() -> userService.signUp(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Keycloak password set failed");

        verify(keycloakAdminService).deleteUser("kc-fail-id");
    }

    // ── getUserById ──────────────────────────────────────────────────────────

    @Test
    void getUserById_found() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        UserDto result = userService.getUserById(1L);

        assertThat(result.getUsername()).isEqualTo("johndoe");
        assertThat(result.getEmail()).isEqualTo("john@test.com");
    }

    @Test
    void getUserById_notFound_throwsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    // ── getUserByUsername ────────────────────────────────────────────────────

    @Test
    void getUserByUsername_found() {
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(sampleUser));

        UserDto result = userService.getUserByUsername("johndoe");
        assertThat(result.getUsername()).isEqualTo("johndoe");
    }

    @Test
    void getUserByUsername_notFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByUsername("unknown"))
                .isInstanceOf(RuntimeException.class);
    }

    // ── getUserByEmail ────────────────────────────────────────────────────────

    @Test
    void getUserByEmail_found() {
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(sampleUser));

        UserDto result = userService.getUserByEmail("john@test.com");
        assertThat(result.getEmail()).isEqualTo("john@test.com");
    }

    // ── getAllUsers ───────────────────────────────────────────────────────────

    @Test
    void getAllUsers_returnsList() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));

        List<UserDto> result = userService.getAllUsers();
        assertThat(result).hasSize(1);
    }

    // ── deleteUser ────────────────────────────────────────────────────────────

    @Test
    void deleteUser_success_deletesFromKeycloakAndDb() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        userService.deleteUser(1L);

        verify(keycloakAdminService).deleteUser("kc-123");
        verify(userRepository).delete(sampleUser);
    }

    @Test
    void deleteUser_notFound_throwsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(RuntimeException.class);
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Test
    void updateUser_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByUsername("newname")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserDto dto = UpdateUserDto.builder()
                .username("newname")
                .firstName("Updated")
                .build();

        UserDto result = userService.updateUser(1L, dto);

        assertThat(result.getUsername()).isEqualTo("newname");
        verify(keycloakAdminService).updateUserProfile(eq("kc-123"), eq("newname"), eq("john@test.com"), eq("Updated"), eq("Doe"));
    }

    @Test
    void updateUser_duplicateUsername_throwsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        UpdateUserDto dto = UpdateUserDto.builder().username("taken").build();

        assertThatThrownBy(() -> userService.updateUser(1L, dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Username already exists");
    }

    @Test
    void updateUser_notFound_throwsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(999L, UpdateUserDto.builder().build()))
                .isInstanceOf(RuntimeException.class);
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.changePassword(1L, "NewPass1", "NewPass1");

        verify(keycloakAdminService).setPassword("kc-123", "NewPass1", false);
    }

    @Test
    void changePassword_mismatch_throwsException() {
        assertThatThrownBy(() -> userService.changePassword(1L, "NewPass1", "Different1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Passwords do not match");
    }

    @Test
    void changePassword_userNotFound_throwsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(999L, "NewPass1", "NewPass1"))
                .isInstanceOf(RuntimeException.class);
    }

    // ── setUserBan ────────────────────────────────────────────────────────────

    @Test
    void setUserBan_banUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto result = userService.setUserBan(1L, true, "Violation", 14);

        assertThat(result.getReasonForBan()).isEqualTo("Violation");
        assertThat(result.getBanPeriod()).isEqualTo(14);
        assertThat(result.getStatus()).isEqualTo(IntegrityStatus.SUSPENDED);
        assertThat(result.getLockedUntil()).isNotNull();
        verify(keycloakAdminService).setBanned("kc-123", true);
    }

    @Test
    void setUserBan_unbanUser() {
        sampleUser.setStatus(IntegrityStatus.SUSPENDED);
        sampleUser.setReasonForBan("Violation");
        sampleUser.setLockedUntil(LocalDateTime.now().plusDays(5));
        sampleUser.setBanPeriod(7);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto result = userService.setUserBan(1L, false, null, null);

        assertThat(result.getReasonForBan()).isNull();
        assertThat(result.getBanPeriod()).isNull();
        assertThat(result.getLockedUntil()).isNull();
        assertThat(result.getStatus()).isEqualTo(IntegrityStatus.SECURE);
        verify(keycloakAdminService).setBanned("kc-123", false);
    }

    @Test
    void setUserBan_defaultBanPeriod_is7Days() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto result = userService.setUserBan(1L, true, null, null);

        assertThat(result.getBanPeriod()).isEqualTo(7);
    }

    // ── setAdminVerification ──────────────────────────────────────────────────

    @Test
    void setAdminVerification_verifyInstructor() {
        sampleUser.setRole(Role.INSTRUCTOR);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto result = userService.setAdminVerification(1L, true, "admin-user");

        assertThat(result.isAdminVerified()).isTrue();
        assertThat(result.getAdminVerifiedBy()).isEqualTo("admin-user");
        verify(keycloakAdminService).setApproved("kc-123", true);
    }

    @Test
    void setAdminVerification_verifyEnterpriseUser_alsoSetsEnterpriseVerified() {
        sampleUser.setRole(Role.ENTERPRISE_USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto result = userService.setAdminVerification(1L, true, "admin-user");

        assertThat(result.isAdminVerified()).isTrue();
        assertThat(result.isEnterpriseVerified()).isTrue();
    }

    // ── forgotPassword ────────────────────────────────────────────────────────

    @Test
    void forgotPassword_existingUser_sendsVerification() {
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(sampleUser));
        var dispatchResult = new ForgotPasswordVerificationService.VerificationDispatchResult(
                ForgotPasswordVerificationService.DeliveryMode.EMAIL, null);
        when(forgotPasswordVerificationService.sendVerificationCode(any(User.class))).thenReturn(dispatchResult);

        var result = userService.forgotPassword("john@test.com");

        assertThat(result.accepted()).isTrue();
        assertThat(result.devFallbackUsed()).isFalse();
    }

    @Test
    void forgotPassword_unknownEmail_stillReturnsAccepted() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        var result = userService.forgotPassword("unknown@test.com");

        assertThat(result.accepted()).isTrue();
        assertThat(result.devFallbackUsed()).isFalse();
    }

    // ── resetPasswordWithVerificationCode ─────────────────────────────────────

    @Test
    void resetPassword_missingEmail_throwsException() {
        assertThatThrownBy(() -> userService.resetPasswordWithVerificationCode("", "code", "NewPass1", "NewPass1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email and verification code are required");
    }

    @Test
    void resetPassword_invalidCode_throwsException() {
        when(forgotPasswordVerificationService.verifyCode("john@test.com", "wrong-code")).thenReturn(false);

        assertThatThrownBy(() -> userService.resetPasswordWithVerificationCode("john@test.com", "wrong-code", "NewPass1", "NewPass1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid or expired verification code");
    }

    @Test
    void resetPassword_success() {
        when(forgotPasswordVerificationService.verifyCode("john@test.com", "valid-code")).thenReturn(true);
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(sampleUser));

        userService.resetPasswordWithVerificationCode("john@test.com", "valid-code", "NewPass1", "NewPass1");

        verify(keycloakAdminService).setPassword("kc-123", "NewPass1", false);
    }

    // ── signIn ────────────────────────────────────────────────────────────────

    @Test
    void signIn_success_returnsAuthResponse() {
        when(keycloakAdminService.loginUser("john@test.com", "Password1"))
                .thenReturn(Map.of(
                        "access_token", createTestJwt("kc-123", "johndoe", "john@test.com", "STUDENT"),
                        "refresh_token", "refresh-token-value",
                        "expires_in", 300
                ));
        when(userRepository.findByKeycloakUserId("kc-123")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SignInDto signInDto = new SignInDto("john@test.com", "Password1");
        AuthResponseDto result = userService.signIn(signInDto);

        assertThat(result.getUsername()).isEqualTo("johndoe");
        assertThat(result.getAccessToken()).isNotNull();
    }

    @Test
    void signIn_invalidCredentials_registersFailedAttempt() {
        when(keycloakAdminService.loginUser("john@test.com", "WrongPass"))
                .thenThrow(new RuntimeException("Invalid credentials"));

        SignInDto signInDto = new SignInDto("john@test.com", "WrongPass");

        assertThatThrownBy(() -> userService.signIn(signInDto))
                .isInstanceOf(RuntimeException.class);
    }

    // ── refreshSession ────────────────────────────────────────────────────────

    @Test
    void refreshSession_success() {
        when(keycloakAdminService.refreshUserSession("refresh-token"))
                .thenReturn(Map.of(
                        "access_token", createTestJwt("kc-123", "johndoe", "john@test.com", "STUDENT"),
                        "refresh_token", "new-refresh-token",
                        "expires_in", 300
                ));
        when(userRepository.findByKeycloakUserId("kc-123")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponseDto result = userService.refreshSession("refresh-token");

        assertThat(result.getUsername()).isEqualTo("johndoe");
    }

    // ── getRecentActivities ────────────────────────────────────────────────────

    @Test
    void getRecentActivities_returnsList() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(interactionEventRepository.findTop50ByKeycloakSubjectOrderByReceivedAtDesc("kc-123"))
                .thenReturn(List.of());

        List<UserActivityDto> result = userService.getRecentActivities(1L, 20);
        assertThat(result).isNotNull();
    }

    // ── getAllUserIds ──────────────────────────────────────────────────────────

    @Test
    void getAllUserIds_returnsList() {
        when(userRepository.findAllUserIds()).thenReturn(List.of(1L, 2L, 3L));

        List<Long> result = userService.getAllUserIds();
        assertThat(result).containsExactly(1L, 2L, 3L);
    }

    // ── getUserIdsByRole ───────────────────────────────────────────────────────

    @Test
    void getUserIdsByRole_returnsList() {
        when(userRepository.findAllUserIdsByRole(Role.STUDENT)).thenReturn(List.of(1L, 2L));

        List<Long> result = userService.getUserIdsByRole(Role.STUDENT);
        assertThat(result).containsExactly(1L, 2L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String createTestJwt(String sub, String preferredUsername, String email, String role) {
        try {
            String header = objectMapper.writeValueAsString(Map.of("alg", "RS256", "typ", "JWT"));
            String payload = objectMapper.writeValueAsString(Map.of(
                    "sub", sub,
                    "preferred_username", preferredUsername,
                    "email", email,
                    "realm_access", Map.of("roles", List.of(role))
            ));
            String headerB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes());
            String payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
            return headerB64 + "." + payloadB64 + ".fake-signature";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
