package esprit.tn.breadandbutteruser.entities;

import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void userBuilderWorks() {
        // Given & When
        LocalDate dob = LocalDate.of(2000, 1, 1);
        LocalDateTime now = LocalDateTime.now();
        
        User user = User.builder()
                .userId(1L)
                .keycloakUserId("kc-123")
                .username("johndoe")
                .email("john@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.STUDENT)
                .dateOfBirth(dob)
                .age(25)
                .country("Tunisia")
                .status(IntegrityStatus.SECURE)
                .accountCreatedAt(now)
                .lastProfileUpdate(now)
                .build();

        // Then
        assertThat(user.getUserId()).isEqualTo(1L);
        assertThat(user.getKeycloakUserId()).isEqualTo("kc-123");
        assertThat(user.getUsername()).isEqualTo("johndoe");
        assertThat(user.getEmail()).isEqualTo("john@test.com");
        assertThat(user.getFirstName()).isEqualTo("John");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getRole()).isEqualTo(Role.STUDENT);
        assertThat(user.getDateOfBirth()).isEqualTo(dob);
        assertThat(user.getAge()).isEqualTo(25);
        assertThat(user.getCountry()).isEqualTo("Tunisia");
        assertThat(user.getStatus()).isEqualTo(IntegrityStatus.SECURE);
    }

    @Test
    void userSettersAndGettersWork() {
        // Given
        User user = new User();
        LocalDate dob = LocalDate.of(1995, 5, 15);
        LocalDateTime now = LocalDateTime.now();

        // When
        user.setUserId(1L);
        user.setKeycloakUserId("kc-456");
        user.setUsername("janedoe");
        user.setEmail("jane@test.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setRole(Role.INSTRUCTOR);
        user.setDateOfBirth(dob);
        user.setAge(30);
        user.setCountry("France");
        user.setStatus(IntegrityStatus.WARNED);
        user.setAccountCreatedAt(now);
        user.setLastProfileUpdate(now);
        user.setFaceBiometricHash("face-hash-123");
        user.setAdminVerified(true);
        user.setEnterpriseVerified(true);
        user.setCompanyName("Tech Corp");
        user.setLockedUntil(now.plusDays(7));
        user.setReasonForBan("Violation");
        user.setBanPeriod(7);

        // Then
        assertThat(user.getUsername()).isEqualTo("janedoe");
        assertThat(user.getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(user.getStatus()).isEqualTo(IntegrityStatus.WARNED);
        assertThat(user.getFaceBiometricHash()).isEqualTo("face-hash-123");
        assertThat(user.isAdminVerified()).isTrue();
        assertThat(user.isEnterpriseVerified()).isTrue();
        assertThat(user.getCompanyName()).isEqualTo("Tech Corp");
        assertThat(user.getLockedUntil()).isEqualTo(now.plusDays(7));
        assertThat(user.getReasonForBan()).isEqualTo("Violation");
        assertThat(user.getBanPeriod()).isEqualTo(7);
    }

    @Test
    void userRoleEnumValues() {
        // Then
        assertThat(Role.ADMIN).isNotNull();
        assertThat(Role.INSTRUCTOR).isNotNull();
        assertThat(Role.STUDENT).isNotNull();
        assertThat(Role.ENTERPRISE_USER).isNotNull();
        assertThat(Role.values()).hasSize(4);
    }

    @Test
    void userIntegrityStatusEnumValues() {
        // Then
        assertThat(IntegrityStatus.SECURE).isNotNull();
        assertThat(IntegrityStatus.WARNED).isNotNull();
        assertThat(IntegrityStatus.SUSPENDED).isNotNull();
        assertThat(IntegrityStatus.FLAGGED).isNotNull();
        assertThat(IntegrityStatus.values()).hasSize(4);
    }

    @Test
    void userRoleFromString() {
        // Then
        assertThat(Role.fromString("ADMIN")).isEqualTo(Role.ADMIN);
        assertThat(Role.fromString("STUDENT")).isEqualTo(Role.STUDENT);
        assertThat(Role.fromString("INSTRUCTOR")).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    void userEqualsAndHashCode() {
        // Given
        User u1 = User.builder().userId(1L).keycloakUserId("kc-123").username("user1").build();
        User u2 = User.builder().userId(1L).keycloakUserId("kc-123").username("user1").build();
        User u3 = User.builder().userId(2L).keycloakUserId("kc-456").username("user2").build();

        // Then
        assertThat(u1).isEqualTo(u2);
        assertThat(u1.hashCode()).isEqualTo(u2.hashCode());
        assertThat(u1).isNotEqualTo(u3);
    }

    @Test
    void userToStringContainsRelevantInfo() {
        // Given
        User user = User.builder()
                .userId(1L)
                .username("johndoe")
                .email("john@test.com")
                .build();

        // When
        String toString = user.toString();

        // Then
        assertThat(toString).contains("userId=1");
        assertThat(toString).contains("username=johndoe");
    }
}
