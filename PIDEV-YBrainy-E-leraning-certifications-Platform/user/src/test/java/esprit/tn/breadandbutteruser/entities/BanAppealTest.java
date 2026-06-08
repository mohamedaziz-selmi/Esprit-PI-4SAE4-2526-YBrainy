package esprit.tn.breadandbutteruser.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BanAppealTest {

    @Test
    void banAppealBuilderWorks() {
        // Given
        User user = User.builder().userId(1L).username("johndoe").build();
        LocalDateTime now = LocalDateTime.now();

        // When
        BanAppeal appeal = BanAppeal.builder()
                .appealId(1L)
                .description("I promise to behave better")
                .appealStatus("PENDING")
                .submittedDate(now)
                .resolvedDate(null)
                .reviewedBy(null)
                .viewed(false)
                .viewedAt(null)
                .viewedBy(null)
                .user(user)
                .build();

        // Then
        assertThat(appeal.getAppealId()).isEqualTo(1L);
        assertThat(appeal.getDescription()).isEqualTo("I promise to behave better");
        assertThat(appeal.getAppealStatus()).isEqualTo("PENDING");
        assertThat(appeal.getSubmittedDate()).isEqualTo(now);
        assertThat(appeal.isViewed()).isFalse();
        assertThat(appeal.getUser()).isEqualTo(user);
    }

    @Test
    void banAppealSettersAndGettersWork() {
        // Given
        BanAppeal appeal = new BanAppeal();
        User user = User.builder().userId(1L).build();
        LocalDateTime now = LocalDateTime.now();

        // When
        appeal.setAppealId(1L);
        appeal.setDescription("Appeal description");
        appeal.setAppealStatus("PENDING");
        appeal.setSubmittedDate(now);
        appeal.setResolvedDate(now.plusDays(1));
        appeal.setReviewedBy("admin");
        appeal.setViewed(true);
        appeal.setViewedAt(now);
        appeal.setViewedBy("moderator");
        appeal.setUser(user);

        // Then
        assertThat(appeal.getAppealId()).isEqualTo(1L);
        assertThat(appeal.getDescription()).isEqualTo("Appeal description");
        assertThat(appeal.getAppealStatus()).isEqualTo("PENDING");
        assertThat(appeal.isViewed()).isTrue();
        assertThat(appeal.getViewedBy()).isEqualTo("moderator");
    }

    @Test
    void banAppealBusinessMethods() {
        // Given
        BanAppeal appeal = new BanAppeal();
        appeal.setDescription("Test appeal");
        appeal.setAppealId(1L);

        // When - submit appeal
        appeal.submitAppeal();

        // Then
        assertThat(appeal.getAppealStatus()).isEqualTo("PENDING");
        assertThat(appeal.isViewed()).isFalse();
        assertThat(appeal.getSubmittedDate()).isNotNull();

        // When - mark viewed
        appeal.markViewed("admin");

        // Then
        assertThat(appeal.isViewed()).isTrue();
        assertThat(appeal.getViewedBy()).isEqualTo("admin");
        assertThat(appeal.getViewedAt()).isNotNull();

        // When - approve
        appeal.approve();

        // Then
        assertThat(appeal.getAppealStatus()).isEqualTo("APPROVED");
        assertThat(appeal.getResolvedDate()).isNotNull();
    }

    @Test
    void banAppealReject() {
        // Given
        BanAppeal appeal = new BanAppeal();
        appeal.setAppealId(1L);

        // When
        appeal.reject();

        // Then
        assertThat(appeal.getAppealStatus()).isEqualTo("REJECTED");
        assertThat(appeal.getResolvedDate()).isNotNull();
    }

    @Test
    void banAppealMarkUnviewed() {
        // Given
        BanAppeal appeal = new BanAppeal();
        appeal.markViewed("admin");
        assertThat(appeal.isViewed()).isTrue();

        // When
        appeal.markUnviewed();

        // Then
        assertThat(appeal.isViewed()).isFalse();
        assertThat(appeal.getViewedAt()).isNull();
        assertThat(appeal.getViewedBy()).isNull();
    }

    @Test
    void banAppealReviewAppeal() {
        // Given
        BanAppeal appeal = new BanAppeal();
        appeal.setAppealId(1L);

        // When & Then - no exception
        appeal.reviewAppeal();
    }

    @Test
    void banAppealEqualsAndHashCode() {
        // Given
        User user = User.builder().userId(1L).build();
        BanAppeal a1 = BanAppeal.builder().appealId(1L).description("Test").user(user).build();
        BanAppeal a2 = BanAppeal.builder().appealId(1L).description("Test").user(user).build();
        BanAppeal a3 = BanAppeal.builder().appealId(2L).description("Other").user(user).build();

        // Then
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
        assertThat(a1).isNotEqualTo(a3);
    }

    @Test
    void banAppealToStringContainsRelevantInfo() {
        // Given
        BanAppeal appeal = BanAppeal.builder()
                .appealId(1L)
                .description("Test appeal")
                .appealStatus("PENDING")
                .build();

        // When
        String toString = appeal.toString();

        // Then
        assertThat(toString).contains("appealId=1");
        assertThat(toString).contains("description=Test appeal");
    }

    @Test
    void banAppealDefaultViewedIsFalse() {
        // Given
        BanAppeal appeal = new BanAppeal();

        // Then
        assertThat(appeal.isViewed()).isFalse();
    }
}
