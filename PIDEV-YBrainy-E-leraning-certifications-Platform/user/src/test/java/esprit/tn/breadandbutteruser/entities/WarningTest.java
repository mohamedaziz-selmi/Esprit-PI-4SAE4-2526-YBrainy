package esprit.tn.breadandbutteruser.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WarningTest {

    @Test
    void warningBuilderWorks() {
        // Given
        User user = User.builder().userId(1L).username("johndoe").build();
        LocalDateTime now = LocalDateTime.now();

        // When
        Warning warning = Warning.builder()
                .warningId(1L)
                .reason("Inappropriate behavior")
                .severity("HIGH")
                .issuedDate(now)
                .issuedBy("admin")
                .user(user)
                .build();

        // Then
        assertThat(warning.getWarningId()).isEqualTo(1L);
        assertThat(warning.getReason()).isEqualTo("Inappropriate behavior");
        assertThat(warning.getSeverity()).isEqualTo("HIGH");
        assertThat(warning.getIssuedDate()).isEqualTo(now);
        assertThat(warning.getIssuedBy()).isEqualTo("admin");
        assertThat(warning.getUser()).isEqualTo(user);
    }

    @Test
    void warningSettersAndGettersWork() {
        // Given
        Warning warning = new Warning();
        User user = User.builder().userId(1L).build();
        LocalDateTime now = LocalDateTime.now();

        // When
        warning.setWarningId(1L);
        warning.setReason("Spam content");
        warning.setSeverity("MEDIUM");
        warning.setIssuedDate(now);
        warning.setIssuedBy("moderator");
        warning.setUser(user);

        // Then
        assertThat(warning.getWarningId()).isEqualTo(1L);
        assertThat(warning.getReason()).isEqualTo("Spam content");
        assertThat(warning.getSeverity()).isEqualTo("MEDIUM");
        assertThat(warning.getIssuedDate()).isEqualTo(now);
        assertThat(warning.getIssuedBy()).isEqualTo("moderator");
    }

    @Test
    void warningBusinessMethods() {
        // Given
        Warning warning = new Warning();
        warning.setReason("Test warning");
        warning.setSeverity("LOW");

        // When & Then - no exceptions
        warning.recordWarning();
        warning.escalate();
    }

    @Test
    void warningEqualsAndHashCode() {
        // Given
        User user = User.builder().userId(1L).build();
        LocalDateTime now = LocalDateTime.now();
        Warning w1 = Warning.builder().warningId(1L).reason("Test").severity("HIGH").user(user).build();
        Warning w2 = Warning.builder().warningId(1L).reason("Test").severity("HIGH").user(user).build();
        Warning w3 = Warning.builder().warningId(2L).reason("Other").severity("LOW").user(user).build();

        // Then
        assertThat(w1).isEqualTo(w2);
        assertThat(w1.hashCode()).isEqualTo(w2.hashCode());
        assertThat(w1).isNotEqualTo(w3);
    }

    @Test
    void warningToStringContainsRelevantInfo() {
        // Given
        Warning warning = Warning.builder()
                .warningId(1L)
                .reason("Test warning")
                .severity("HIGH")
                .build();

        // When
        String toString = warning.toString();

        // Then
        assertThat(toString).contains("warningId=1");
        assertThat(toString).contains("reason=Test warning");
        assertThat(toString).contains("severity=HIGH");
    }

    @Test
    void warningNoArgsConstructorCreatesEmptyObject() {
        // When
        Warning warning = new Warning();

        // Then
        assertThat(warning.getWarningId()).isNull();
        assertThat(warning.getReason()).isNull();
        assertThat(warning.getSeverity()).isNull();
        assertThat(warning.getIssuedDate()).isNull();
        assertThat(warning.getIssuedBy()).isNull();
    }
}
