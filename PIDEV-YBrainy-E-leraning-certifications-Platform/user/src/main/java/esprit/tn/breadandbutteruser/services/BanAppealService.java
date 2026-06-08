package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.dto.BanAppealRequestDto;
import esprit.tn.breadandbutteruser.dto.BanAppealResponseDto;
import esprit.tn.breadandbutteruser.entities.BanAppeal;
import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.repositories.BanAppealRepository;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BanAppealService {

    private final BanAppealRepository banAppealRepository;
    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

    public BanAppealResponseDto submit(BanAppealRequestDto request, User user) {
        User persistedUser = resolveAppealUser(user);
        assertCanSubmitAppeal(persistedUser);
        String description = normalizeDescription(request != null ? request.getDescription() : null);

        BanAppeal banAppeal = BanAppeal.builder()
                .description(description)
                .user(persistedUser)
                .build();

        banAppeal.submitAppeal();
        return toDto(banAppealRepository.save(banAppeal));
    }

    public BanAppealResponseDto submitForEmail(String email, String description) {
        if (!StringUtils.hasText(email)) {
            throw new RuntimeException("Email is required");
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new RuntimeException("No account found for this email"));

        BanAppealRequestDto request = BanAppealRequestDto.builder()
                .description(description)
                .build();
        return submit(request, user);
    }

    @Transactional(readOnly = true)
    public BanAppealResponseDto getById(Long id) {
        BanAppeal appeal = banAppealRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ban appeal not found with ID: " + id));
        return toDto(appeal);
    }

    @Transactional(readOnly = true)
    public List<BanAppealResponseDto> getAll() {
        return banAppealRepository.findAllByOrderBySubmittedDateDesc().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BanAppealResponseDto> getByUserId(Long userId) {
        return banAppealRepository.findByUserUserIdOrderBySubmittedDateDesc(userId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BanAppealResponseDto> getByStatus(String status) {
        return banAppealRepository.findByAppealStatusOrderBySubmittedDateDesc(status).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BanAppealResponseDto> getByViewed(boolean viewed) {
        return banAppealRepository.findByViewedOrderBySubmittedDateDesc(viewed).stream().map(this::toDto).collect(Collectors.toList());
    }

    public BanAppealResponseDto markViewed(Long id, String viewedBy) {
        BanAppeal appeal = banAppealRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ban appeal not found with ID: " + id));
        String actor = StringUtils.hasText(viewedBy) ? viewedBy.trim() : "admin";
        appeal.markViewed(actor);
        return toDto(banAppealRepository.save(appeal));
    }

    public BanAppealResponseDto markUnviewed(Long id) {
        BanAppeal appeal = banAppealRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ban appeal not found with ID: " + id));
        appeal.markUnviewed();
        return toDto(banAppealRepository.save(appeal));
    }

    public BanAppealResponseDto approve(Long id, String reviewedBy) {
        BanAppeal appeal = banAppealRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ban appeal not found with ID: " + id));
        String actor = StringUtils.hasText(reviewedBy) ? reviewedBy.trim() : "admin";
        appeal.setReviewedBy(actor);
        appeal.approve();
        appeal.markViewed(actor);

        User user = appeal.getUser();
        if (user != null) {
            user.setLockedUntil(null);
            user.setReasonForBan(null);
            user.setBanPeriod(null);
            if (user.getStatus() == IntegrityStatus.SUSPENDED) {
                user.setStatus(IntegrityStatus.SECURE);
            }
            user.setLastProfileUpdate(LocalDateTime.now());
            userRepository.save(user);

            resolveKeycloakUserId(user).ifPresent(keycloakId -> {
                try {
                    keycloakAdminService.setBanned(keycloakId, false);
                } catch (RuntimeException ex) {
                    log.warn("Failed to lift Keycloak ban for user {} after appeal approval: {}", user.getUserId(), ex.getMessage());
                }
            });
        }

        return toDto(banAppealRepository.save(appeal));
    }

    public BanAppealResponseDto reject(Long id, String reviewedBy) {
        BanAppeal appeal = banAppealRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ban appeal not found with ID: " + id));
        appeal.setReviewedBy(reviewedBy);
        appeal.reject();
        appeal.markViewed(reviewedBy);
        return toDto(banAppealRepository.save(appeal));
    }

    public void delete(Long id) {
        if (!banAppealRepository.existsById(id)) {
            throw new RuntimeException("Ban appeal not found with ID: " + id);
        }
        banAppealRepository.deleteById(id);
    }

    private BanAppealResponseDto toDto(BanAppeal appeal) {
        return BanAppealResponseDto.builder()
                .appealId(appeal.getAppealId())
                .description(appeal.getDescription())
                .appealStatus(appeal.getAppealStatus())
                .submittedDate(appeal.getSubmittedDate())
                .resolvedDate(appeal.getResolvedDate())
                .reviewedBy(appeal.getReviewedBy())
                .viewed(appeal.isViewed())
                .viewedAt(appeal.getViewedAt())
                .viewedBy(appeal.getViewedBy())
                .userId(appeal.getUser() != null ? appeal.getUser().getUserId() : null)
                .build();
    }

    private User resolveAppealUser(User user) {
        if (user == null || user.getUserId() == null) {
            throw new RuntimeException("Appeal user identity is required");
        }

        return userRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found for appeal"));
    }

    private void assertCanSubmitAppeal(User user) {
        if (!isRestricted(user)) {
            throw new RuntimeException("Ban appeals are only available while your account is restricted.");
        }

        boolean pendingExists = banAppealRepository.existsByUserUserIdAndAppealStatus(
                user.getUserId(),
                "PENDING"
        );
        if (pendingExists) {
            throw new RuntimeException("Ban appeal already submitted. Please wait for admin response.");
        }

        Optional<BanAppeal> latestAppeal = banAppealRepository.findTopByUserUserIdOrderBySubmittedDateDesc(user.getUserId());
        if (latestAppeal.isPresent() && "REJECTED".equalsIgnoreCase(latestAppeal.get().getAppealStatus())) {
            String waitMessage = formatRemainingBanWait(user.getLockedUntil());
            if (waitMessage != null) {
                throw new RuntimeException("Your latest ban appeal was rejected. You are still banned for " + waitMessage + ".");
            }
            throw new RuntimeException("Your latest ban appeal was rejected. Please wait until your ban period ends.");
        }
    }

    private boolean isRestricted(User user) {
        if (user == null) {
            return false;
        }

        String normalizedStatus = user.getStatus() != null ? user.getStatus().name().toUpperCase(Locale.ROOT) : "";
        if (user.getStatus() == IntegrityStatus.SUSPENDED || "BANNED".equals(normalizedStatus)) {
            return true;
        }

        if (StringUtils.hasText(user.getReasonForBan())) {
            return true;
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            return true;
        }

        Optional<String> keycloakUserId = resolveKeycloakUserId(user);
        if (keycloakUserId.isPresent()) {
            return keycloakAdminService.isUserEnabled(keycloakUserId.get())
                    .map(enabled -> !enabled)
                    .orElse(false);
        }

        return false;
    }

    private String normalizeDescription(String description) {
        String normalized = description != null ? description.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            throw new RuntimeException("Description is required");
        }
        if (normalized.length() < 10 || normalized.length() > 1000) {
            throw new RuntimeException("Description must be between 10 and 1000 characters");
        }
        return normalized;
    }

    private Optional<String> resolveKeycloakUserId(User user) {
        if (user == null) {
            return Optional.empty();
        }
        if (StringUtils.hasText(user.getKeycloakUserId())) {
            return Optional.of(user.getKeycloakUserId().trim());
        }
        return keycloakAdminService.findUserIdByUsernameOrEmail(user.getUsername(), user.getEmail());
    }

    private String formatRemainingBanWait(LocalDateTime lockedUntil) {
        if (lockedUntil == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!lockedUntil.isAfter(now)) {
            return null;
        }

        long hours = ChronoUnit.HOURS.between(now, lockedUntil);
        long days = Math.max(1L, (hours + 23L) / 24L);
        return days + (days == 1 ? " day" : " days");
    }
}
