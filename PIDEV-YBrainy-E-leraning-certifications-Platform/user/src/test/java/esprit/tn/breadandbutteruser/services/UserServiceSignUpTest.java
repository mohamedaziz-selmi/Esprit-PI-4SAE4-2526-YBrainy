package esprit.tn.breadandbutteruser.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import esprit.tn.breadandbutteruser.dto.SignUpDto;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import esprit.tn.breadandbutteruser.messaging.UserEventPublisher;
import esprit.tn.breadandbutteruser.repositories.BanAppealRepository;
import esprit.tn.breadandbutteruser.repositories.InteractionEventRepository;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import esprit.tn.breadandbutteruser.repositories.WarningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UserServiceSignUpTest {

    private UserRepository userRepository;
    private KeycloakAdminService keycloakAdminService;
    private SignUpChallengeService signUpChallengeService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        keycloakAdminService = mock(KeycloakAdminService.class);
        signUpChallengeService = mock(SignUpChallengeService.class);

        userService = new UserService(
                userRepository,
                mock(WarningRepository.class),
                mock(BanAppealRepository.class),
                mock(InteractionEventRepository.class),
                keycloakAdminService,
                signUpChallengeService,
                mock(FaceBiometricEngineService.class),
                mock(ForgotPasswordVerificationService.class),
                mock(UserEventPublisher.class),
                new ObjectMapper()
        );
    }

    @Test
    void rejectsWeakPasswordBeforeTouchingPersistenceOrKeycloak() {
        SignUpDto dto = SignUpDto.builder()
                .username("signup-user")
                .email("signup@example.com")
                .password("password123")
                .confirmPassword("password123")
                .role(Role.STUDENT)
                .age(22)
                .country("Tunisia")
                .build();

        assertThatThrownBy(() -> userService.signUp(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Password must include uppercase, lowercase, and numeric characters");

        verifyNoInteractions(userRepository, signUpChallengeService, keycloakAdminService);
    }

    @Test
    void requiresChallengeBeforeProvisioningKeycloakUser() {
        SignUpDto dto = SignUpDto.builder()
                .username("signup-user")
                .email("signup@example.com")
                .password("Password123")
                .confirmPassword("Password123")
                .role(Role.STUDENT)
                .age(22)
                .country("Tunisia")
                .build();

        doThrow(new RuntimeException("Complete the personalized security check before creating an account."))
                .when(signUpChallengeService)
                .validateChallenge(null, null, null);

        assertThatThrownBy(() -> userService.signUp(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Complete the personalized security check before creating an account.");

        verifyNoInteractions(keycloakAdminService);
    }
}
