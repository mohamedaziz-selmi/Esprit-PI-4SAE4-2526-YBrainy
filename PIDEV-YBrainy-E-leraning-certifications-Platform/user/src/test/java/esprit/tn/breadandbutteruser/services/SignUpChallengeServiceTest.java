package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.dto.SignUpChallengeRequestDto;
import esprit.tn.breadandbutteruser.dto.SignUpChallengeResponseDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignUpChallengeServiceTest {

    private final SignUpChallengeService service = new SignUpChallengeService();

    @Test
    void createChallengeUsesProfileBasedRecommendation() {
        SignUpChallengeResponseDto franceChallenge = service.createChallenge(
                new SignUpChallengeRequestDto(60, "France", null)
        );
        SignUpChallengeResponseDto tunisiaChallenge = service.createChallenge(
                new SignUpChallengeRequestDto(20, "Tunisia", null)
        );

        assertThat(franceChallenge.recommendedMode()).isEqualTo("history");
        assertThat(franceChallenge.selectedMode()).isEqualTo("history");
        assertThat(franceChallenge.availableModes()).hasSizeLessThanOrEqualTo(3);
        assertThat(franceChallenge.availableModes())
                .extracting(SignUpChallengeResponseDto.ModeOptionDto::key)
                .contains("jigsaw", "math");

        assertThat(tunisiaChallenge.recommendedMode()).isEqualTo("math");
        assertThat(tunisiaChallenge.selectedMode()).isEqualTo("math");
        assertThat(tunisiaChallenge.availableModes()).hasSizeLessThanOrEqualTo(3);
        assertThat(tunisiaChallenge.availableModes())
                .extracting(SignUpChallengeResponseDto.ModeOptionDto::key)
                .contains("jigsaw", "history");
    }

    @Test
    void createChallengeAllowsSwitchingToAnAlternativeMode() {
        SignUpChallengeResponseDto challenge = service.createChallenge(
                new SignUpChallengeRequestDto(60, "France", "jigsaw")
        );

        assertThat(challenge.recommendedMode()).isEqualTo("history");
        assertThat(challenge.selectedMode()).isEqualTo("jigsaw");
        assertThat(challenge.challengeKind()).isEqualTo("flag_jigsaw");
        assertThat(challenge.flagJigsaw()).isNotNull();
        assertThat(challenge.availableModes())
                .extracting(SignUpChallengeResponseDto.ModeOptionDto::key)
                .contains("history", "jigsaw");
    }

    @Test
    void validateChallengeRejectsModeMismatch() {
        SignUpChallengeResponseDto challenge = service.createChallenge(
                new SignUpChallengeRequestDto(25, "Tunisia", "math")
        );

        String wrongMode = challenge.selectedMode().equals("math") ? "history" : "math";
        String anyChoiceId = challenge.choices().get(0).id();

        assertThatThrownBy(() -> service.validateChallenge(challenge.token(), wrongMode, anyChoiceId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no longer matches");
    }
}
