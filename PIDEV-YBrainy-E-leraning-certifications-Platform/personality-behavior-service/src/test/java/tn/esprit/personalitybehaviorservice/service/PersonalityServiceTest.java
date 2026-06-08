package tn.esprit.personalitybehaviorservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.personalitybehaviorservice.client.UserClient;
import tn.esprit.personalitybehaviorservice.dto.BehaviorRequestDto;
import tn.esprit.personalitybehaviorservice.dto.PersonalityRequestDto;
import tn.esprit.personalitybehaviorservice.dto.PersonalityResponseDto;
import tn.esprit.personalitybehaviorservice.entity.Behavior;
import tn.esprit.personalitybehaviorservice.entity.Personality;
import tn.esprit.personalitybehaviorservice.messaging.PersonalityEventPublisher;
import tn.esprit.personalitybehaviorservice.repository.BehaviorRepository;
import tn.esprit.personalitybehaviorservice.repository.PersonalityRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalityServiceTest {

    @Mock PersonalityRepository personalityRepository;
    @Mock BehaviorRepository behaviorRepository;
    @Mock UserClient userClient;
    @Mock PersonalityEventPublisher eventPublisher;
    @InjectMocks PersonalityService service;

    private Personality samplePersonality(String id, Long userId) {
        return Personality.builder()
                .id(id)
                .userId(userId)
                .visualLearningPct(40.0)
                .auditoryLearningPct(30.0)
                .kinestheticLearningPct(30.0)
                .careerAlignmentScore(75.0)
                .cognitiveLoadTolerance(60.0)
                .careerGoals(List.of("Developer", "Architect"))
                .build();
    }

    private PersonalityRequestDto sampleRequest(Long userId) {
        return PersonalityRequestDto.builder()
                .userId(userId)
                .visualLearningPct(40.0)
                .auditoryLearningPct(30.0)
                .kinestheticLearningPct(30.0)
                .careerAlignmentScore(75.0)
                .cognitiveLoadTolerance(60.0)
                .careerGoals(List.of("Developer"))
                .build();
    }

    /* ─── create ─── */

    @Test
    @DisplayName("create() saves personality and publishes event")
    void create_success() {
        PersonalityRequestDto request = sampleRequest(1L);
        Personality saved = samplePersonality("abc123", 1L);

        when(userClient.getUserById(1L)).thenReturn(null);
        when(personalityRepository.existsByUserId(1L)).thenReturn(false);
        when(personalityRepository.save(any())).thenReturn(saved);
        doNothing().when(eventPublisher).publishPersonalityCreated(any());

        PersonalityResponseDto result = service.create(request);

        assertThat(result.getUserId()).isEqualTo(1L);
        verify(personalityRepository).save(any());
        verify(eventPublisher).publishPersonalityCreated(any());
    }

    @Test
    @DisplayName("create() throws when personality already exists for user")
    void create_duplicate_throws() {
        PersonalityRequestDto request = sampleRequest(1L);
        when(userClient.getUserById(1L)).thenReturn(null);
        when(personalityRepository.existsByUserId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("create() with embedded behavior builds behavior document")
    void create_withBehavior_savesBehavior() {
        BehaviorRequestDto behaviorReq = BehaviorRequestDto.builder()
                .agitationLevelPct(10.0)
                .focusScorePct(80.0)
                .engagementIndexPct(70.0)
                .learningPacePercentile(60.0)
                .fraudProbabilityScore(5.0)
                .build();

        PersonalityRequestDto request = sampleRequest(2L);
        request.setBehavior(behaviorReq);

        Personality saved = samplePersonality("xyz", 2L);
        saved.setBehavior(Behavior.builder()
                .userId(2L)
                .focusScorePct(80.0)
                .build());

        when(userClient.getUserById(2L)).thenReturn(null);
        when(personalityRepository.existsByUserId(2L)).thenReturn(false);
        when(personalityRepository.save(any())).thenReturn(saved);
        doNothing().when(eventPublisher).publishPersonalityCreated(any());

        PersonalityResponseDto result = service.create(request);

        assertThat(result.getBehavior()).isNotNull();
    }

    @Test
    @DisplayName("create() wraps exception when user service is unreachable")
    void create_userServiceDown_throws() {
        PersonalityRequestDto request = sampleRequest(99L);
        when(userClient.getUserById(99L)).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unavailable");
    }

    /* ─── getById ─── */

    @Test
    @DisplayName("getById() returns DTO when personality exists")
    void getById_found() {
        Personality p = samplePersonality("id1", 5L);
        when(personalityRepository.findById("id1")).thenReturn(Optional.of(p));

        PersonalityResponseDto result = service.getById("id1");

        assertThat(result.getPersonalityId()).isEqualTo("id1");
        assertThat(result.getUserId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getById() throws RuntimeException when not found")
    void getById_notFound_throws() {
        when(personalityRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    /* ─── getByUserId ─── */

    @Test
    @DisplayName("getByUserId() returns DTO for given user")
    void getByUserId_found() {
        Personality p = samplePersonality("id2", 7L);
        when(personalityRepository.findByUserId(7L)).thenReturn(Optional.of(p));

        PersonalityResponseDto result = service.getByUserId(7L);

        assertThat(result.getUserId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("getByUserId() throws when no personality for user")
    void getByUserId_notFound_throws() {
        when(personalityRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByUserId(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    /* ─── getAll ─── */

    @Test
    @DisplayName("getAll() returns list of all personalities")
    void getAll_returnsList() {
        when(personalityRepository.findAll())
                .thenReturn(List.of(samplePersonality("a", 1L), samplePersonality("b", 2L)));

        List<PersonalityResponseDto> result = service.getAll();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getAll() returns empty list when none exist")
    void getAll_empty() {
        when(personalityRepository.findAll()).thenReturn(Collections.emptyList());

        assertThat(service.getAll()).isEmpty();
    }

    /* ─── update ─── */

    @Test
    @DisplayName("update() modifies only provided fields and leaves others unchanged")
    void update_partialUpdate() {
        Personality existing = samplePersonality("id3", 3L);
        when(personalityRepository.findById("id3")).thenReturn(Optional.of(existing));
        when(personalityRepository.save(any())).thenReturn(existing);
        doNothing().when(eventPublisher).publishPersonalityUpdated(any());

        PersonalityRequestDto req = PersonalityRequestDto.builder()
                .visualLearningPct(55.0)
                .build();

        service.update("id3", req);

        assertThat(existing.getVisualLearningPct()).isEqualTo(55.0);
        assertThat(existing.getAuditoryLearningPct()).isEqualTo(30.0);
        verify(personalityRepository).save(existing);
        verify(eventPublisher).publishPersonalityUpdated(any());
    }

    @Test
    @DisplayName("update() throws when personality not found")
    void update_notFound_throws() {
        when(personalityRepository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("gone", sampleRequest(1L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    /* ─── delete ─── */

    @Test
    @DisplayName("delete() removes personality by id")
    void delete_success() {
        when(personalityRepository.existsById("id4")).thenReturn(true);
        doNothing().when(personalityRepository).deleteById("id4");

        service.delete("id4");

        verify(personalityRepository).deleteById("id4");
    }

    @Test
    @DisplayName("delete() throws when personality does not exist")
    void delete_notFound_throws() {
        when(personalityRepository.existsById("none")).thenReturn(false);

        assertThatThrownBy(() -> service.delete("none"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    /* ─── deleteByUserId ─── */

    @Test
    @DisplayName("deleteByUserId() removes both personality and behavior records")
    void deleteByUserId_deletesAll() {
        doNothing().when(personalityRepository).deleteByUserId(10L);
        doNothing().when(behaviorRepository).deleteByUserId(10L);

        service.deleteByUserId(10L);

        verify(personalityRepository).deleteByUserId(10L);
        verify(behaviorRepository).deleteByUserId(10L);
    }

    @Test
    @DisplayName("deleteByUserId() is a no-op when userId is null")
    void deleteByUserId_null_skips() {
        service.deleteByUserId(null);

        verifyNoInteractions(personalityRepository);
        verifyNoInteractions(behaviorRepository);
    }
}
