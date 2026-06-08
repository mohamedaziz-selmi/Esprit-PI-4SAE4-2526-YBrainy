package esprit.tn.breadandbutteruser.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import esprit.tn.breadandbutteruser.dto.AdminUserOverviewDto;
import esprit.tn.breadandbutteruser.dto.AuthResponseDto;
import esprit.tn.breadandbutteruser.dto.InternalUserResponse;
import esprit.tn.breadandbutteruser.dto.SignInDto;
import esprit.tn.breadandbutteruser.dto.SignUpDto;
import esprit.tn.breadandbutteruser.dto.UpdateUserDto;
import esprit.tn.breadandbutteruser.dto.UserActivityDto;
import esprit.tn.breadandbutteruser.dto.UserDto;
import esprit.tn.breadandbutteruser.entities.BanAppeal;
import esprit.tn.breadandbutteruser.entities.InteractionEvent;
import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import esprit.tn.breadandbutteruser.messaging.UserEventPublisher;
import esprit.tn.breadandbutteruser.repositories.BanAppealRepository;
import esprit.tn.breadandbutteruser.repositories.InteractionEventRepository;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import esprit.tn.breadandbutteruser.repositories.WarningRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {
    private static final int MAX_CONSECUTIVE_FAILED_PASSWORD_ATTEMPTS = 3;
    private static final int FAILED_LOGIN_COOLDOWN_SECONDS = 50;

    private final UserRepository userRepository;
    private final WarningRepository warningRepository;
    private final BanAppealRepository banAppealRepository;
    private final InteractionEventRepository interactionEventRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final SignUpChallengeService signUpChallengeService;
    private final FaceBiometricEngineService faceBiometricEngineService;
    private final ForgotPasswordVerificationService forgotPasswordVerificationService;
    private final UserEventPublisher userEventPublisher;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, LoginThrottleState> loginThrottleStates = new ConcurrentHashMap<>();

    public AuthResponseDto signUp(SignUpDto signUpDto) {
        log.info("Signing up new user: {}", signUpDto.getUsername());

        validatePasswordPair(signUpDto.getPassword(), signUpDto.getConfirmPassword());

        if (userRepository.existsByUsername(signUpDto.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(signUpDto.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Role role = signUpDto.getRole() != null ? signUpDto.getRole() : Role.INSTRUCTOR;

        // STUDENT is a first-class role (no special signup requirements)

        if (role == Role.ENTERPRISE_USER) {
            if (signUpDto.getCompanyName() == null || signUpDto.getCompanyName().isBlank()) {
                throw new RuntimeException("companyName is required for ENTERPRISE_USER");
            }
        }

        signUpChallengeService.validateChallenge(
                signUpDto.getChallengeToken(),
                signUpDto.getChallengeMode(),
                signUpDto.getChallengeAnswer());

        String keycloakUserId = null;
        User savedUser;
        try {
            // Create user in Keycloak first so login via Keycloak works.
            keycloakUserId = keycloakAdminService.createUser(
                    signUpDto.getUsername(),
                    signUpDto.getEmail(),
                    signUpDto.getFirstName(),
                    signUpDto.getLastName(),
                    true);
            keycloakAdminService.setPassword(keycloakUserId, signUpDto.getPassword(), false);
            keycloakAdminService.assignRealmRole(keycloakUserId, role);

            if (role == Role.ENTERPRISE_USER) {
                keycloakAdminService.setCompany(keycloakUserId, signUpDto.getCompanyName());
                keycloakAdminService.setApproved(keycloakUserId, false);
            }

            User user = User.builder()
                    .keycloakUserId(keycloakUserId)
                    .username(signUpDto.getUsername())
                    .firstName(signUpDto.getFirstName())
                    .lastName(signUpDto.getLastName())
                    .email(signUpDto.getEmail())
                    .role(role)
                    .dateOfBirth(signUpDto.getDateOfBirth())
                    .profilePicture(normalizeProfilePictureReference(signUpDto.getProfilePicture()))
                    .age(resolveAge(signUpDto.getDateOfBirth(), signUpDto.getAge()))
                    .address(signUpDto.getAddress())
                    .city(signUpDto.getCity())
                    .country(signUpDto.getCountry())
                    .sex(signUpDto.getSex())
                    .companyName(role == Role.ENTERPRISE_USER ? signUpDto.getCompanyName() : null)
                    .enterpriseOnboardedAt(role == Role.ENTERPRISE_USER ? LocalDateTime.now() : null)
                    .accountCreatedAt(LocalDateTime.now())
                    .lastProfileUpdate(LocalDateTime.now())
                    .build();

            savedUser = userRepository.save(user);
        } catch (RuntimeException ex) {
            if (StringUtils.hasText(keycloakUserId)) {
                try {
                    keycloakAdminService.deleteUser(keycloakUserId);
                    log.warn("Rolled back Keycloak user {} after signup failure", keycloakUserId);
                } catch (RuntimeException cleanupEx) {
                    log.error("Failed to rollback Keycloak user {} after signup failure: {}", keycloakUserId,
                            cleanupEx.getMessage());
                }
            }
            throw ex;
        }
        log.info("User signed up successfully with ID: {}", savedUser.getUserId());
        userEventPublisher.publishCreated(savedUser);

        return AuthResponseDto.builder()
                .userId(savedUser.getUserId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .role(savedUser.getRole().name())
                .build();
    }

    public AuthResponseDto signIn(SignInDto signInDto) {
        log.info("Signing in user: {}", signInDto.getEmail());

        String loginIdentifier = normalizeLoginIdentifier(signInDto.getEmail());
        assertLoginNotCoolingDown(loginIdentifier);

        Map<String, Object> tokenResponse;
        try {
            tokenResponse = keycloakAdminService.loginUser(signInDto.getEmail(), signInDto.getPassword());
        } catch (RuntimeException ex) {
            if (isInvalidCredentialsFailure(ex)) {
                LoginThrottleState state = registerFailedLoginAttempt(loginIdentifier);
                if (state.cooldownUntil() != null && state.cooldownUntil().isAfter(LocalDateTime.now())) {
                    throw new RuntimeException(buildLoginCooldownMessage(state.cooldownUntil()));
                }
            }
            if (isRestrictedLoginFailure(ex)) {
                throw new RuntimeException(buildRestrictedLoginMessage(signInDto.getEmail()), ex);
            }
            throw ex;
        }
        Map<String, Object> claims = decodeJwtClaims(stringValue(tokenResponse.get("access_token")));
        String keycloakUserId = stringValue(claims.get("sub"));
        String tokenEmail = firstNonBlank(
                stringValue(claims.get("email")),
                signInDto.getEmail());
        String tokenUsername = firstNonBlank(
                stringValue(claims.get("preferred_username")),
                tokenEmail);
        Role tokenRole = extractPrimaryRole(claims).orElse(Role.INSTRUCTOR);

        clearLoginThrottle(loginIdentifier, tokenEmail, tokenUsername);

        User user = resolveOrProvisionLocalUser(keycloakUserId, tokenUsername, tokenEmail, tokenRole);
        updateLoginTracking(user);

        log.info("User signed in successfully: {}", user.getUsername());

        return buildAuthResponse(user, tokenResponse, null);
    }

    public AuthResponseDto refreshSession(String refreshToken) {
        Map<String, Object> tokenResponse = keycloakAdminService.refreshUserSession(refreshToken);
        Map<String, Object> claims = decodeJwtClaims(stringValue(tokenResponse.get("access_token")));

        String keycloakUserId = stringValue(claims.get("sub"));
        String tokenEmail = stringValue(claims.get("email"));
        String tokenUsername = firstNonBlank(
                stringValue(claims.get("preferred_username")),
                tokenEmail);
        Role tokenRole = extractPrimaryRole(claims).orElse(Role.INSTRUCTOR);

        User user = resolveOrProvisionLocalUser(keycloakUserId, tokenUsername, tokenEmail, tokenRole);
        return buildAuthResponse(user, tokenResponse, refreshToken);
    }

    public UserDto registerFaceBiometric(Long userId, MultipartFile image) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        String newHash = faceBiometricEngineService.computeFaceHash(image);
        faceBiometricEngineService.storeEnrollmentImage(newHash, image, user.getFaceBiometricHash());

        user.setFaceBiometricHash(newHash);
        user.setLastProfileUpdate(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        resolveKeycloakUserId(savedUser).ifPresent(keycloakId -> {
            try {
                keycloakAdminService.setFaceBiometricHash(keycloakId, newHash);
            } catch (RuntimeException ex) {
                log.warn("Failed to sync face biometric hash to Keycloak for user {}: {}", userId, ex.getMessage());
            }
        });

        return convertToDto(savedUser);
    }

    public UserDto removeFaceBiometric(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        faceBiometricEngineService.deleteEnrollmentImage(user.getFaceBiometricHash());
        user.setFaceBiometricHash(null);
        user.setLastProfileUpdate(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        resolveKeycloakUserId(savedUser).ifPresent(keycloakId -> {
            try {
                keycloakAdminService.setFaceBiometricHash(keycloakId, null);
            } catch (RuntimeException ex) {
                log.warn("Failed to clear face biometric hash in Keycloak for user {}: {}", userId, ex.getMessage());
            }
        });

        return convertToDto(savedUser);
    }

    public FaceSignInSession startFaceSignIn(MultipartFile image) {
        String matchedHash = faceBiometricEngineService.matchFaceHash(image);
        if (!StringUtils.hasText(matchedHash)) {
            throw new RuntimeException("Face not recognized. Try again or sign in with email.");
        }

        User user = userRepository.findByFaceBiometricHash(matchedHash)
                .orElseThrow(() -> new RuntimeException("Face match found, but no linked user exists."));

        String keycloakUserId = resolveKeycloakUserId(user)
                .orElseThrow(() -> new RuntimeException("Unable to resolve the matched user's Keycloak account."));

        KeycloakAdminService.ImpersonationSession session = keycloakAdminService
                .startUserImpersonationSession(keycloakUserId);
        updateLoginTracking(user);
        return new FaceSignInSession(session.setCookieHeaders(), session.redirectUrl());
    }

    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        return convertToDto(user);
    }

    public UserDto getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
        return convertToDto(user);
    }

    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return convertToDto(user);
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> getFirstUserByRole(Role role) {
        return userRepository.findFirstByRoleOrderByUserIdAsc(role).map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<Long> getAllUserIds() {
        return userRepository.findAllUserIds();
    }

    @Transactional(readOnly = true)
    public List<Long> getUserIdsByRole(Role role) {
        return userRepository.findAllUserIdsByRole(role);
    }

    public void deleteUser(Long id) {
        log.info("Deleting user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

        Optional<String> keycloakUserId = Optional.ofNullable(user.getKeycloakUserId())
                .filter(StringUtils::hasText)
                .or(() -> keycloakAdminService.findUserIdByUsernameOrEmail(user.getUsername(), user.getEmail()));
        if (keycloakUserId.isPresent()) {
            keycloakAdminService.deleteUser(keycloakUserId.get());
        } else {
            log.warn("Keycloak user not found during delete for local user ID {} (username={}, email={})",
                    id, user.getUsername(), user.getEmail());
        }

        userRepository.delete(user);
        userEventPublisher.publishDeleted(user);
        log.info("User deleted successfully");
    }

    public UserDto updateUser(Long id, UpdateUserDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

        String originalUsername = user.getUsername();
        String originalEmail = user.getEmail();

        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            String requestedUsername = dto.getUsername().trim();
            if (!requestedUsername.equals(user.getUsername()) && userRepository.existsByUsername(requestedUsername)) {
                throw new RuntimeException("Username already exists");
            }
            user.setUsername(requestedUsername);
        }
        if (dto.getFirstName() != null)
            user.setFirstName(normalizeNullableText(dto.getFirstName()));
        if (dto.getLastName() != null)
            user.setLastName(normalizeNullableText(dto.getLastName()));
        if (dto.getDateOfBirth() != null) {
            user.setDateOfBirth(dto.getDateOfBirth());
            user.setAge(calculateAge(dto.getDateOfBirth()));
        } else if (dto.getAge() != null) {
            user.setAge(dto.getAge());
        }
        if (dto.getProfilePicture() != null)
            user.setProfilePicture(normalizeProfilePictureReference(dto.getProfilePicture()));
        if (dto.getAddress() != null)
            user.setAddress(normalizeNullableText(dto.getAddress()));
        if (dto.getCity() != null)
            user.setCity(normalizeNullableText(dto.getCity()));
        if (dto.getCountry() != null)
            user.setCountry(normalizeNullableText(dto.getCountry()));
        if (dto.getSex() != null)
            user.setSex(normalizeNullableText(dto.getSex()));
        if (dto.getCompanyName() != null && user.getRole() == Role.ENTERPRISE_USER) {
            user.setCompanyName(normalizeNullableText(dto.getCompanyName()));
        }

        user.setLastProfileUpdate(LocalDateTime.now());

        Optional<String> keycloakUserId = Optional.ofNullable(user.getKeycloakUserId())
                .filter(StringUtils::hasText)
                .or(() -> keycloakAdminService.findUserIdByUsernameOrEmail(originalUsername, originalEmail));

        if (keycloakUserId.isPresent()) {
            keycloakAdminService.updateUserProfile(
                    keycloakUserId.get(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName());
            if (dto.getCompanyName() != null && user.getRole() == Role.ENTERPRISE_USER
                    && StringUtils.hasText(user.getCompanyName())) {
                keycloakAdminService.setCompany(keycloakUserId.get(), user.getCompanyName());
            }
        } else {
            log.warn("Keycloak user not found during update for local user ID {} (username={}, email={})",
                    id, originalUsername, originalEmail);
        }

        try {
            User savedUser = userRepository.save(user);
            userEventPublisher.publishUpdated(savedUser);
            return convertToDto(savedUser);
        } catch (RuntimeException ex) {
            log.error(
                    "Local DB update failed after Keycloak sync for user ID {}. Manual reconciliation may be required.",
                    id);
            throw ex;
        }
    }

    public ForgotPasswordResult forgotPassword(String email) {
        return userRepository.findByEmail(email).map(user -> {
            ForgotPasswordVerificationService.VerificationDispatchResult dispatch = forgotPasswordVerificationService
                    .sendVerificationCode(user);
            boolean fallback = dispatch.deliveryMode() == ForgotPasswordVerificationService.DeliveryMode.DEV_FALLBACK;
            return new ForgotPasswordResult(true, fallback, fallback ? dispatch.fallbackCode() : null);
        }).orElseGet(() -> {
            log.info("Forgot-password requested for unknown email: {}", email);
            return new ForgotPasswordResult(true, false, null);
        });
    }

    public void resetPasswordWithVerificationCode(String email, String code, String newPassword,
            String confirmPassword) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(code)) {
            throw new RuntimeException("Email and verification code are required");
        }
        validatePasswordPair(newPassword, confirmPassword);

        String normalizedEmail = email.trim();
        if (!forgotPasswordVerificationService.verifyCode(normalizedEmail, code)) {
            throw new RuntimeException("Invalid or expired verification code");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("User not found for the provided email"));

        String keycloakUserId = resolveKeycloakUserId(user)
                .orElseThrow(() -> new RuntimeException("Unable to resolve account identity for password reset"));

        keycloakAdminService.setPassword(keycloakUserId, newPassword, false);
        log.info("Password reset completed via verification code for local user ID {}", user.getUserId());
    }

    public void changePassword(Long userId, String newPassword, String confirmPassword) {
        validatePasswordPair(newPassword, confirmPassword);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        String keycloakUserId = resolveKeycloakUserId(user)
                .orElseThrow(() -> new RuntimeException("Unable to resolve account identity for password change"));

        keycloakAdminService.setPassword(keycloakUserId, newPassword, false);
        user.setLastProfileUpdate(LocalDateTime.now());
        userRepository.save(user);
        log.info("Password changed successfully for local user ID {}", user.getUserId());
    }

    @Transactional(readOnly = true)
    public List<AdminUserOverviewDto> getAdminUserOverview(String search, String filter) {
        return buildAdminUserOverview(search, filter);
    }

    @Transactional(readOnly = true)
    public AdminOverviewPageResult getAdminUserOverviewPage(String search, String filter, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        List<AdminUserOverviewDto> allUsers = buildAdminUserOverview(search, filter);
        long totalElements = allUsers.size();
        int totalPages = totalElements == 0L ? 1 : (int) Math.ceil((double) totalElements / safeSize);

        int maxPageIndex = Math.max(0, totalPages - 1);
        int safePage = Math.min(Math.max(0, page), maxPageIndex);

        long offset = (long) safePage * safeSize;
        int fromIndex = offset >= totalElements ? (int) totalElements : (int) offset;
        int toIndex = (int) Math.min(totalElements, fromIndex + safeSize);
        List<AdminUserOverviewDto> content = allUsers.subList(fromIndex, toIndex);

        long pendingVerificationCount = allUsers.stream()
                .filter(dto -> dto.isVerificationRequired() && !dto.isVerificationApproved())
                .count();
        long bannedCount = allUsers.stream()
                .filter(AdminUserOverviewDto::isBanned)
                .count();
        long warnedCount = allUsers.stream()
                .filter(dto -> dto.getWarningCount() > 0)
                .count();
        long activeRecentlyCount = allUsers.stream()
                .filter(dto -> dto.getActivityCountLast30Days() > 0)
                .count();

        return new AdminOverviewPageResult(
                content,
                totalElements,
                totalPages,
                safePage,
                safeSize,
                pendingVerificationCount,
                bannedCount,
                warnedCount,
                activeRecentlyCount);
    }

    private List<AdminUserOverviewDto> buildAdminUserOverview(String search, String filter) {
        String normalizedSearch = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;
        String normalizedFilter = StringUtils.hasText(filter) ? filter.trim().toLowerCase(Locale.ROOT) : "all";
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        return userRepository.findAll().stream()
                .filter(user -> matchesSearch(user, normalizedSearch))
                .map(user -> toAdminOverview(user, since))
                .filter(dto -> matchesAdminFilter(dto, normalizedFilter))
                .sorted(Comparator
                        .comparing(AdminUserOverviewDto::isVerificationRequired, Comparator.reverseOrder())
                        .thenComparing(AdminUserOverviewDto::isBanned, Comparator.reverseOrder())
                        .thenComparing(AdminUserOverviewDto::getLastProfileUpdate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AdminUserOverviewDto::getAccountCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public record AdminOverviewPageResult(
            List<AdminUserOverviewDto> content,
            long totalElements,
            int totalPages,
            int page,
            int size,
            long pendingVerificationCount,
            long bannedCount,
            long warnedCount,
            long activeRecentlyCount) {
    }

    public UserDto setAdminVerification(Long userId, boolean verified, String actorName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        LocalDateTime now = LocalDateTime.now();
        user.setAdminVerified(verified);
        user.setAdminVerifiedAt(verified ? now : null);
        user.setAdminVerifiedBy(verified ? firstNonBlank(actorName, "admin") : null);

        if (user.getRole() == Role.ENTERPRISE_USER) {
            user.setEnterpriseVerified(verified);
        }

        user.setLastProfileUpdate(now);

        resolveKeycloakUserId(user).ifPresent(keycloakId -> {
            try {
                keycloakAdminService.setApproved(keycloakId, verified);
            } catch (RuntimeException ex) {
                log.warn("Failed to sync verification approval to Keycloak for local user {}: {}",
                        userId, ex.getMessage());
            }
        });

        return convertToDto(userRepository.save(user));
    }

    public UserDto setUserBan(Long userId, boolean banned, String reasonForBan, Integer banPeriodDays) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        LocalDateTime now = LocalDateTime.now();
        if (banned) {
            int resolvedPeriod = (banPeriodDays != null && banPeriodDays > 0) ? banPeriodDays : 7;
            String resolvedReason = StringUtils.hasText(reasonForBan) ? reasonForBan.trim()
                    : "Administrative suspension";
            user.setReasonForBan(resolvedReason);
            user.setBanPeriod(resolvedPeriod);
            user.setLockedUntil(now.plusDays(resolvedPeriod));
            user.setStatus(IntegrityStatus.SUSPENDED);
        } else {
            user.setReasonForBan(null);
            user.setBanPeriod(null);
            user.setLockedUntil(null);
            if (user.getStatus() == IntegrityStatus.SUSPENDED) {
                user.setStatus(IntegrityStatus.SECURE);
            }
        }
        user.setLastProfileUpdate(now);

        resolveKeycloakUserId(user).ifPresent(keycloakId -> {
            try {
                keycloakAdminService.setBanned(keycloakId, banned);
            } catch (RuntimeException ex) {
                log.warn("Failed to sync ban status to Keycloak for local user {}: {}", userId, ex.getMessage());
            }
        });

        return convertToDto(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserActivityDto> getRecentActivities(Long userId, int limit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<InteractionEvent> events = recentEventsForUser(user).stream()
                .sorted(Comparator.comparing(InteractionEvent::getReceivedAt).reversed())
                .limit(safeLimit)
                .collect(Collectors.toList());

        return events.stream()
                .map(this::toUserActivityDto)
                .collect(Collectors.toList());
    }

    private UserDto convertToDto(User user) {
        return UserDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .dateOfBirth(user.getDateOfBirth())
                .profilePicture(user.getProfilePicture())
                .age(user.getAge())
                .address(user.getAddress())
                .city(user.getCity())
                .country(user.getCountry())
                .sex(user.getSex())
                .companyName(user.getRole() == Role.ENTERPRISE_USER ? user.getCompanyName() : null)
                .adminVerified(user.isAdminVerified())
                .adminVerifiedAt(user.getAdminVerifiedAt())
                .adminVerifiedBy(user.getAdminVerifiedBy())
                .enterpriseVerified(user.isEnterpriseVerified())
                .enterpriseOnboardedAt(user.getEnterpriseOnboardedAt())
                .faceBiometricRegistered(StringUtils.hasText(user.getFaceBiometricHash()))
                .streakDays(user.getStreakDays())
                .xp(user.getXp())
                .level(user.getLevel())
                .lastActiveAt(user.getLastActiveAt())
                .bio(user.getBio())
                .lastLogin(user.getLastLogin())
                .accountCreatedAt(user.getAccountCreatedAt())
                .lastProfileUpdate(user.getLastProfileUpdate())
                .lockedUntil(user.getLockedUntil())
                .reasonForBan(user.getReasonForBan())
                .banPeriod(user.getBanPeriod())
                .status(user.getStatus())
                .build();
    }

    private AdminUserOverviewDto toAdminOverview(User user, LocalDateTime since) {
        Long warningCountValue = warningRepository.countByUserUserId(user.getUserId());
        long warningCount = warningCountValue != null ? warningCountValue : 0L;
        long activityCountLast30Days = countRecentActivities(user, since);
        LocalDateTime latestActivityAt = latestActivityAt(user);
        boolean verificationRequired = user.getRole() == Role.INSTRUCTOR || user.getRole() == Role.ENTERPRISE_USER;
        boolean verificationApproved = user.getRole() == Role.ENTERPRISE_USER ? user.isEnterpriseVerified()
                : user.isAdminVerified();
        boolean banned = user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());

        return AdminUserOverviewDto.builder()
                .userId(user.getUserId())
                .keycloakUserId(user.getKeycloakUserId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .profilePicture(user.getProfilePicture())
                .companyName(user.getRole() == Role.ENTERPRISE_USER ? user.getCompanyName() : null)
                .city(user.getCity())
                .country(user.getCountry())
                .status(user.getStatus())
                .adminVerified(user.isAdminVerified())
                .enterpriseVerified(user.isEnterpriseVerified())
                .verificationRequired(verificationRequired)
                .verificationApproved(verificationApproved)
                .banned(banned)
                .reasonForBan(user.getReasonForBan())
                .banPeriod(user.getBanPeriod())
                .lockedUntil(user.getLockedUntil())
                .streakDays(user.getStreakDays())
                .lastLogin(user.getLastLogin())
                .accountCreatedAt(user.getAccountCreatedAt())
                .lastProfileUpdate(user.getLastProfileUpdate())
                .warningCount(warningCount)
                .activityCountLast30Days(activityCountLast30Days)
                .latestActivityAt(latestActivityAt)
                .build();
    }

    private boolean matchesSearch(User user, String search) {
        if (!StringUtils.hasText(search)) {
            return true;
        }
        return Stream.of(
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCompanyName(),
                user.getCity(),
                user.getCountry(),
                user.getRole() != null ? user.getRole().name() : null)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(search));
    }

    private boolean matchesAdminFilter(AdminUserOverviewDto dto, String filter) {
        return switch (filter) {
            case "pending_verification" -> dto.isVerificationRequired() && !dto.isVerificationApproved();
            case "banned" -> dto.isBanned();
            case "warned" -> dto.getWarningCount() > 0;
            case "instructor" -> dto.getRole() == Role.INSTRUCTOR;
            case "enterprise" -> dto.getRole() == Role.ENTERPRISE_USER;
            case "student" -> dto.getRole() == Role.STUDENT;
            case "admin" -> dto.getRole() == Role.ADMIN;
            default -> true;
        };
    }

    private long countRecentActivities(User user, LocalDateTime since) {
        if (StringUtils.hasText(user.getKeycloakUserId())) {
            return interactionEventRepository.countByKeycloakSubjectAndReceivedAtAfter(user.getKeycloakUserId(), since);
        }
        if (StringUtils.hasText(user.getEmail())) {
            return interactionEventRepository.countByEmailIgnoreCaseAndReceivedAtAfter(user.getEmail(), since);
        }
        return 0L;
    }

    private LocalDateTime latestActivityAt(User user) {
        if (StringUtils.hasText(user.getKeycloakUserId())) {
            return interactionEventRepository.findTopByKeycloakSubjectOrderByReceivedAtDesc(user.getKeycloakUserId())
                    .map(InteractionEvent::getReceivedAt)
                    .orElse(null);
        }
        if (StringUtils.hasText(user.getEmail())) {
            return interactionEventRepository.findTopByEmailIgnoreCaseOrderByReceivedAtDesc(user.getEmail())
                    .map(InteractionEvent::getReceivedAt)
                    .orElse(null);
        }
        return null;
    }

    private List<InteractionEvent> recentEventsForUser(User user) {
        if (StringUtils.hasText(user.getKeycloakUserId())) {
            return interactionEventRepository.findTop50ByKeycloakSubjectOrderByReceivedAtDesc(user.getKeycloakUserId());
        }
        if (StringUtils.hasText(user.getEmail())) {
            return interactionEventRepository.findTop50ByEmailIgnoreCaseOrderByReceivedAtDesc(user.getEmail());
        }
        return List.of();
    }

    private UserActivityDto toUserActivityDto(InteractionEvent event) {
        return UserActivityDto.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .route(event.getRoute())
                .durationMs(event.getDurationMs())
                .occurredAtEpochMs(event.getOccurredAtEpochMs())
                .receivedAt(event.getReceivedAt())
                .role(event.getRole())
                .build();
    }

    private Optional<String> resolveKeycloakUserId(User user) {
        return Optional.ofNullable(user.getKeycloakUserId())
                .filter(StringUtils::hasText)
                .or(() -> keycloakAdminService.findUserIdByUsernameOrEmail(user.getUsername(), user.getEmail()));
    }

    private User resolveOrProvisionLocalUser(String keycloakUserId, String username, String email, Role role) {
        if (!StringUtils.hasText(keycloakUserId)) {
            throw new RuntimeException("Keycloak token missing subject");
        }

        Optional<User> byKeycloakId = userRepository.findByKeycloakUserId(keycloakUserId);
        if (byKeycloakId.isPresent()) {
            User existing = byKeycloakId.get();
            applyKeycloakIdentityRefresh(existing, username, email, role);
            return existing;
        }

        Optional<User> byEmail = StringUtils.hasText(email) ? userRepository.findByEmail(email) : Optional.empty();
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            existing.setKeycloakUserId(keycloakUserId);
            applyKeycloakIdentityRefresh(existing, username, email, role);
            return existing;
        }

        log.info("Creating local user record on-demand for Keycloak subject {}", keycloakUserId);
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .keycloakUserId(keycloakUserId)
                .username(makeUniqueUsername(fallbackUsername(username, email)))
                .email(email)
                .role(role)
                .accountCreatedAt(now)
                .lastProfileUpdate(now)
                .build();
        User savedUser = userRepository.save(user);
        userEventPublisher.publishCreated(savedUser);
        return savedUser;
    }

    private void applyKeycloakIdentityRefresh(User user, String username, String email, Role role) {
        boolean changed = false;
        if (StringUtils.hasText(username) && !username.equals(user.getUsername())) {
            // Avoid duplicate collision if another local user already holds the username.
            if (!userRepository.existsByUsername(username) || username.equals(user.getUsername())) {
                user.setUsername(username);
                changed = true;
            }
        }
        if (StringUtils.hasText(email) && !email.equalsIgnoreCase(user.getEmail())) {
            if (!userRepository.existsByEmail(email) || email.equalsIgnoreCase(user.getEmail())) {
                user.setEmail(email);
                changed = true;
            }
        }
        if (role != null && user.getRole() != role) {
            user.setRole(role);
            changed = true;
        }
        if (changed) {
            user.setLastProfileUpdate(LocalDateTime.now());
            User savedUser = userRepository.save(user);
            userEventPublisher.publishUpdated(savedUser);
        }
    }

    private void updateLoginTracking(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate lastLoginDate = user.getLastLogin() != null ? user.getLastLogin().toLocalDate() : null;

        if (lastLoginDate == null || lastLoginDate.isBefore(today.minusDays(1))) {
            user.setStreakDays(1);
        } else if (lastLoginDate.isBefore(today)) {
            user.setStreakDays(user.getStreakDays() + 1);
        }

        user.setLastLogin(now);
        User savedUser = userRepository.save(user);
        userEventPublisher.publishLogin(savedUser);
    }

    private String makeUniqueUsername(String base) {
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private String fallbackUsername(String username, String email) {
        if (StringUtils.hasText(username)) {
            return username;
        }
        if (StringUtils.hasText(email) && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        if (StringUtils.hasText(email)) {
            return email;
        }
        return "user";
    }

    private Integer resolveAge(LocalDate dateOfBirth, Integer fallbackAge) {
        if (dateOfBirth == null) {
            return fallbackAge;
        }
        return calculateAge(dateOfBirth);
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private String normalizeProfilePictureReference(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized != null && normalized.regionMatches(true, 0, "data:image/", 0, "data:image/".length())) {
            throw new RuntimeException(
                    "Profile image must be uploaded first. Send the returned image URL instead of base64 data.");
        }
        return normalized;
    }

    private int calculateAge(LocalDate dateOfBirth) {
        LocalDate today = LocalDate.now();
        if (dateOfBirth.isAfter(today)) {
            throw new RuntimeException("Date of birth cannot be in the future");
        }
        return Period.between(dateOfBirth, today).getYears();
    }

    private boolean isInvalidCredentialsFailure(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null) {
            return false;
        }
        return "Invalid credentials".equalsIgnoreCase(ex.getMessage().trim());
    }

    private void assertLoginNotCoolingDown(String loginIdentifier) {
        if (!StringUtils.hasText(loginIdentifier)) {
            return;
        }

        LoginThrottleState state = loginThrottleStates.get(loginIdentifier);
        if (state == null || state.cooldownUntil() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!state.cooldownUntil().isAfter(now)) {
            loginThrottleStates.remove(loginIdentifier, state);
            return;
        }

        throw new RuntimeException(buildLoginCooldownMessage(state.cooldownUntil()));
    }

    private LoginThrottleState registerFailedLoginAttempt(String loginIdentifier) {
        if (!StringUtils.hasText(loginIdentifier)) {
            return new LoginThrottleState(1, null);
        }

        LocalDateTime now = LocalDateTime.now();
        return loginThrottleStates.compute(loginIdentifier, (key, existing) -> {
            if (existing != null && existing.cooldownUntil() != null && existing.cooldownUntil().isAfter(now)) {
                return existing;
            }

            int nextFailures = 1;
            if (existing != null) {
                nextFailures = Math.max(0, existing.consecutiveFailures()) + 1;
            }

            if (nextFailures >= MAX_CONSECUTIVE_FAILED_PASSWORD_ATTEMPTS) {
                return new LoginThrottleState(0, now.plusSeconds(FAILED_LOGIN_COOLDOWN_SECONDS));
            }

            return new LoginThrottleState(nextFailures, null);
        });
    }

    private void clearLoginThrottle(String... identifiers) {
        for (String identifier : identifiers) {
            String normalized = normalizeLoginIdentifier(identifier);
            if (StringUtils.hasText(normalized)) {
                loginThrottleStates.remove(normalized);
            }
        }
    }

    private String normalizeLoginIdentifier(String identifier) {
        return StringUtils.hasText(identifier) ? identifier.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String buildLoginCooldownMessage(LocalDateTime cooldownUntil) {
        long secondsRemaining = Math.max(1L, ChronoUnit.SECONDS.between(LocalDateTime.now(), cooldownUntil));
        return "Too many failed password attempts. Try again in " + secondsRemaining + " second(s).";
    }

    private boolean isRestrictedLoginFailure(RuntimeException ex) {
        if (ex == null || !StringUtils.hasText(ex.getMessage())) {
            return false;
        }
        String message = ex.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("restricted")
                || message.contains("disabled")
                || message.contains("banned")
                || message.contains("ban appeal");
    }

    private String buildRestrictedLoginMessage(String email) {
        String generic = "Account is currently restricted. Submit a ban appeal to request review.";
        if (!StringUtils.hasText(email)) {
            return generic;
        }

        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(email.trim());
        if (maybeUser.isEmpty()) {
            return generic;
        }

        User user = maybeUser.get();
        Long userId = user.getUserId();
        if (userId == null) {
            return generic;
        }

        boolean hasPendingAppeal = banAppealRepository.existsByUserUserIdAndAppealStatus(userId, "PENDING");
        if (hasPendingAppeal) {
            return "Ban appeal submitted. Please wait for admin response.";
        }

        Optional<BanAppeal> latestAppeal = banAppealRepository.findTopByUserUserIdOrderBySubmittedDateDesc(userId);
        if (latestAppeal.isPresent() && "REJECTED".equalsIgnoreCase(latestAppeal.get().getAppealStatus())) {
            String remainingDays = formatRemainingBanDays(user.getLockedUntil());
            if (remainingDays != null) {
                return "Your ban appeal was rejected. You are still banned for " + remainingDays + ".";
            }
            return "Your ban appeal was rejected. Please wait until your ban period ends.";
        }

        String remainingDays = formatRemainingBanDays(user.getLockedUntil());
        if (remainingDays != null) {
            return "Account is currently banned for " + remainingDays + ".";
        }

        return generic;
    }

    private String formatRemainingBanDays(LocalDateTime lockedUntil) {
        if (lockedUntil == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!lockedUntil.isAfter(now)) {
            return null;
        }

        long hoursRemaining = ChronoUnit.HOURS.between(now, lockedUntil);
        long daysRemaining = Math.max(1L, (hoursRemaining + 23L) / 24L);
        return daysRemaining + (daysRemaining == 1 ? " day" : " days");
    }

    private Optional<Role> extractPrimaryRole(Map<String, Object> claims) {
        Object realmAccessObj = claims.get("realm_access");
        if (!(realmAccessObj instanceof Map<?, ?> realmAccess)) {
            return Optional.empty();
        }
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof List<?> roles)) {
            return Optional.empty();
        }

        Set<String> normalized = new LinkedHashSet<>();
        roles.stream().map(String::valueOf).forEach(r -> normalized.add(r.toUpperCase()));
        for (Role candidate : List.of(Role.ADMIN, Role.ENTERPRISE_USER, Role.INSTRUCTOR, Role.STUDENT)) {
            if (normalized.contains(candidate.name())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> decodeJwtClaims(String jwt) {
        if (!StringUtils.hasText(jwt)) {
            throw new RuntimeException("Keycloak token missing");
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new RuntimeException("Malformed JWT token");
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(payloadBytes, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Unable to parse Keycloak token payload");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private void validatePasswordPair(String newPassword, String confirmPassword) {
        if (!StringUtils.hasText(newPassword) || !StringUtils.hasText(confirmPassword)) {
            throw new RuntimeException("Password and confirmation are required");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("Passwords do not match");
        }
        if (newPassword.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters");
        }
        boolean hasUpper = newPassword.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = newPassword.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = newPassword.chars().anyMatch(Character::isDigit);
        if (!(hasUpper && hasLower && hasDigit)) {
            throw new RuntimeException("Password must include uppercase, lowercase, and numeric characters");
        }
    }

    private Long longValue(Object value) {
        if (value == null)
            return null;
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private AuthResponseDto buildAuthResponse(User user, Map<String, Object> tokenResponse,
            String fallbackRefreshToken) {
        return AuthResponseDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .accessToken(stringValue(tokenResponse.get("access_token")))
                .refreshToken(firstNonBlank(
                        stringValue(tokenResponse.get("refresh_token")),
                        fallbackRefreshToken))
                .expiresIn(longValue(tokenResponse.get("expires_in")))
                .build();
    }

    private record LoginThrottleState(int consecutiveFailures, LocalDateTime cooldownUntil) {
    }

    public record ForgotPasswordResult(boolean accepted, boolean devFallbackUsed, String devFallbackCode) {
    }

    public record FaceSignInSession(List<String> setCookieHeaders, String redirectUrl) {
    }

    // Forum profile methods
    @Transactional(readOnly = true)
    public esprit.tn.breadandbutteruser.dto.ForumProfileResponse getForumProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return esprit.tn.breadandbutteruser.dto.ForumProfileResponse.fromUser(user);
    }

    @Transactional(readOnly = true)
    public esprit.tn.breadandbutteruser.dto.ForumProfileResponse getForumProfileByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return esprit.tn.breadandbutteruser.dto.ForumProfileResponse.fromUser(user);
    }

    @Transactional(readOnly = true)
    public List<esprit.tn.breadandbutteruser.dto.ForumProfileResponse> getLeaderboard() {
        return userRepository.findAllByOrderByXpDesc()
                .stream()
                .map(esprit.tn.breadandbutteruser.dto.ForumProfileResponse::fromUser)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<esprit.tn.breadandbutteruser.dto.ForumProfileResponse> getAllForumProfiles() {
        return userRepository.findAll()
                .stream()
                .map(esprit.tn.breadandbutteruser.dto.ForumProfileResponse::fromUser)
                .collect(Collectors.toList());
    }

    @Transactional
    public esprit.tn.breadandbutteruser.dto.ForumProfileResponse updateForumProfile(Long userId, String username, String bio) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        if (username != null && !username.isBlank()) user.setUsername(username);
        if (bio != null) user.setBio(bio);
        user.setLastProfileUpdate(LocalDateTime.now());
        return esprit.tn.breadandbutteruser.dto.ForumProfileResponse.fromUser(userRepository.save(user));
    }

    @Transactional
    public esprit.tn.breadandbutteruser.dto.ForumProfileResponse updateForumProfile(Long userId,
            esprit.tn.breadandbutteruser.controllers.XpController.UpdateForumProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            user.setUsername(request.getUsername());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        user.setLastProfileUpdate(LocalDateTime.now());

        return esprit.tn.breadandbutteruser.dto.ForumProfileResponse.fromUser(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public InternalUserResponse getInternalUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return InternalUserResponse.fromUser(user);
    }

    @Transactional(readOnly = true)
    public Optional<InternalUserResponse> getInternalUserByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakUserId(keycloakId)
                .map(InternalUserResponse::fromUser);
    }
}
