package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.entities.BanAppeal;
import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.Warning;
import esprit.tn.breadandbutteruser.repositories.BanAppealRepository;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import esprit.tn.breadandbutteruser.repositories.WarningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component("authz")
@RequiredArgsConstructor
public class AuthorizationHelper {

    private final UserRepository userRepository;
    private final WarningRepository warningRepository;
    private final BanAppealRepository banAppealRepository;

    public boolean isSelfUserId(Authentication authentication, Long userId) {
        return resolveCurrentUser(authentication)
                .map(user -> userId != null && userId.equals(user.getUserId()))
                .orElse(false);
    }

    public boolean isSelfUsername(Authentication authentication, String username) {
        return resolveCurrentUser(authentication)
                .map(user -> StringUtils.hasText(username) && username.equalsIgnoreCase(user.getUsername()))
                .orElse(false);
    }

    public boolean isSelfEmail(Authentication authentication, String email) {
        return resolveCurrentUser(authentication)
                .map(user -> StringUtils.hasText(email) && email.equalsIgnoreCase(user.getEmail()))
                .orElse(false);
    }

    public boolean isWarningOwner(Authentication authentication, Long warningId) {
        if (warningId == null) {
            return false;
        }
        Optional<Warning> warning = warningRepository.findById(warningId);
        return warning.map(w -> {
            Long ownerId = w.getUser() != null ? w.getUser().getUserId() : null;
            return isSelfUserId(authentication, ownerId);
        }).orElse(false);
    }

    public boolean isBanAppealOwner(Authentication authentication, Long appealId) {
        if (appealId == null) {
            return false;
        }
        Optional<BanAppeal> appeal = banAppealRepository.findById(appealId);
        return appeal.map(a -> {
            Long ownerId = a.getUser() != null ? a.getUser().getUserId() : null;
            return isSelfUserId(authentication, ownerId);
        }).orElse(false);
    }

    public boolean isPersonalityOwner(Authentication authentication, Long personalityId) {
        if (personalityId == null) {
            return false;
        }
        return userRepository.findByPersonalityPersonalityId(personalityId)
                .map(user -> isSelfUserId(authentication, user.getUserId()))
                .orElse(false);
    }

    public boolean isBehaviorOwner(Authentication authentication, Long behaviorId) {
        if (behaviorId == null) {
            return false;
        }
        return userRepository.findByPersonalityBehaviorBehaviorId(behaviorId)
                .map(user -> isSelfUserId(authentication, user.getUserId()))
                .orElse(false);
    }

    public User requireCurrentUser(Jwt jwt) {
        return resolveCurrentUser(jwt)
                .orElseThrow(() -> new RuntimeException("No local user mapped to current token"));
    }

    public String actorName(Jwt jwt) {
        if (jwt == null) {
            return "system";
        }
        String email = jwt.getClaimAsString("email");
        if (StringUtils.hasText(email)) {
            return email;
        }
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (StringUtils.hasText(preferredUsername)) {
            return preferredUsername;
        }
        return Optional.ofNullable(jwt.getSubject()).filter(StringUtils::hasText).orElse("system");
    }

    private Optional<User> resolveCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return resolveCurrentUser(jwt);
        }
        return Optional.empty();
    }

    private Optional<User> resolveCurrentUser(Jwt jwt) {
        if (jwt == null) {
            return Optional.empty();
        }

        String subject = jwt.getSubject();
        if (StringUtils.hasText(subject)) {
            Optional<User> bySubject = userRepository.findByKeycloakUserId(subject);
            if (bySubject.isPresent()) {
                return bySubject;
            }
        }

        String email = jwt.getClaimAsString("email");
        if (StringUtils.hasText(email)) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }

        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (StringUtils.hasText(preferredUsername)) {
            return userRepository.findByUsername(preferredUsername);
        }

        return Optional.empty();
    }
}
