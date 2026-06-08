package esprit.tn.breadandbutteruser.repositories;

import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByKeycloakUserId_returnsUser() {
        // Given
        User user = createUser("kc-123", "johndoe", "john@test.com", Role.STUDENT);
        entityManager.persist(user);
        entityManager.flush();

        // When
        Optional<User> result = userRepository.findByKeycloakUserId("kc-123");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("johndoe");
    }

    @Test
    void findByKeycloakUserId_notFound() {
        // When
        Optional<User> result = userRepository.findByKeycloakUserId("nonexistent");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void findByUsername_returnsUser() {
        // Given
        User user = createUser("kc-123", "johndoe", "john@test.com", Role.STUDENT);
        entityManager.persist(user);
        entityManager.flush();

        // When
        Optional<User> result = userRepository.findByUsername("johndoe");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("john@test.com");
    }

    @Test
    void findByEmail_returnsUser() {
        // Given
        User user = createUser("kc-123", "johndoe", "john@test.com", Role.STUDENT);
        entityManager.persist(user);
        entityManager.flush();

        // When
        Optional<User> result = userRepository.findByEmail("john@test.com");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("johndoe");
    }

    @Test
    void existsByUsername_returnsCorrectBoolean() {
        // Given
        User user = createUser("kc-123", "johndoe", "john@test.com", Role.STUDENT);
        entityManager.persist(user);
        entityManager.flush();

        // Then
        assertThat(userRepository.existsByUsername("johndoe")).isTrue();
        assertThat(userRepository.existsByUsername("janedoe")).isFalse();
    }

    @Test
    void existsByEmail_returnsCorrectBoolean() {
        // Given
        User user = createUser("kc-123", "johndoe", "john@test.com", Role.STUDENT);
        entityManager.persist(user);
        entityManager.flush();

        // Then
        assertThat(userRepository.existsByEmail("john@test.com")).isTrue();
        assertThat(userRepository.existsByEmail("jane@test.com")).isFalse();
    }

    @Test
    void findByFaceBiometricHash_returnsUser() {
        // Given
        User user = createUser("kc-123", "johndoe", "john@test.com", Role.STUDENT);
        user.setFaceBiometricHash("hash123");
        entityManager.persist(user);
        entityManager.flush();

        // When
        Optional<User> result = userRepository.findByFaceBiometricHash("hash123");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("johndoe");
    }

    @Test
    void findAllByRole_returnsUsersWithRole() {
        // Given
        entityManager.persist(createUser("kc-1", "student1", "s1@test.com", Role.STUDENT));
        entityManager.persist(createUser("kc-2", "student2", "s2@test.com", Role.STUDENT));
        entityManager.persist(createUser("kc-3", "admin", "admin@test.com", Role.ADMIN));
        entityManager.flush();

        // When
        List<User> result = userRepository.findAllByRole(Role.STUDENT);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(User::getRole).containsOnly(Role.STUDENT);
    }

    @Test
    void findFirstByRole_returnsUser() {
        // Given
        entityManager.persist(createUser("kc-1", "student1", "s1@test.com", Role.STUDENT));
        entityManager.persist(createUser("kc-2", "student2", "s2@test.com", Role.STUDENT));
        entityManager.flush();

        // When
        Optional<User> result = userRepository.findFirstByRoleOrderByUserIdAsc(Role.STUDENT);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void findAllUserIds_returnsAllIds() {
        // Given
        User u1 = createUser("kc-1", "user1", "u1@test.com", Role.STUDENT);
        User u2 = createUser("kc-2", "user2", "u2@test.com", Role.STUDENT);
        entityManager.persist(u1);
        entityManager.persist(u2);
        entityManager.flush();

        // When
        List<Long> result = userRepository.findAllUserIds();

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void findAllUserIdsByRole_returnsFilteredIds() {
        // Given
        entityManager.persist(createUser("kc-1", "student1", "s1@test.com", Role.STUDENT));
        entityManager.persist(createUser("kc-2", "student2", "s2@test.com", Role.STUDENT));
        entityManager.persist(createUser("kc-3", "admin", "admin@test.com", Role.ADMIN));
        entityManager.flush();

        // When
        List<Long> result = userRepository.findAllUserIdsByRole(Role.STUDENT);

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void findByEmailIgnoreCase_returnsUser() {
        // Given
        User user = createUser("kc-1", "user1", "u1@test.com", Role.STUDENT);
        entityManager.persist(user);
        entityManager.flush();

        // When
        Optional<User> result = userRepository.findByEmailIgnoreCase("U1@TEST.COM");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("user1");
    }

    @Test
    void findFirstByRole_returnsEmptyWhenMissing() {
        // Given
        entityManager.persist(createUser("kc-1", "student1", "s1@test.com", Role.STUDENT));
        entityManager.persist(createUser("kc-2", "student2", "s2@test.com", Role.STUDENT));
        entityManager.flush();

        // When
        Optional<User> result = userRepository.findFirstByRoleOrderByUserIdAsc(Role.ADMIN);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void saveAndRetrieveUser() {
        // Given
        User user = new User();
        user.setKeycloakUserId("kc-new");
        user.setUsername("newuser");
        user.setEmail("new@test.com");
        user.setFirstName("New");
        user.setLastName("User");
        user.setRole(Role.STUDENT);
        user.setDateOfBirth(LocalDate.of(2000, 1, 1));
        user.setAge(25);
        user.setCountry("Tunisia");
        user.setStatus(IntegrityStatus.SECURE);
        user.setAccountCreatedAt(LocalDateTime.now());
        user.setLastProfileUpdate(LocalDateTime.now());

        // When
        User saved = userRepository.save(user);

        // Then
        assertThat(saved.getUserId()).isNotNull();
        User retrieved = entityManager.find(User.class, saved.getUserId());
        assertThat(retrieved.getUsername()).isEqualTo("newuser");
        assertThat(retrieved.getEmail()).isEqualTo("new@test.com");
    }

    private User createUser(String keycloakId, String username, String email, Role role) {
        return User.builder()
                .keycloakUserId(keycloakId)
                .username(username)
                .email(email)
                .firstName("First")
                .lastName("Last")
                .role(role)
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .age(25)
                .country("Tunisia")
                .status(IntegrityStatus.SECURE)
                .accountCreatedAt(LocalDateTime.now())
                .lastProfileUpdate(LocalDateTime.now())
                .build();
    }
}
