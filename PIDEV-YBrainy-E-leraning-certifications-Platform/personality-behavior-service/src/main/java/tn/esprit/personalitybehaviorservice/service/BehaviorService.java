package tn.esprit.personalitybehaviorservice.service;

import tn.esprit.personalitybehaviorservice.dto.BehaviorRequestDto;
import tn.esprit.personalitybehaviorservice.dto.BehaviorResponseDto;
import tn.esprit.personalitybehaviorservice.entity.Behavior;
import tn.esprit.personalitybehaviorservice.client.UserClient;
import tn.esprit.personalitybehaviorservice.messaging.PersonalityEventPublisher;
import tn.esprit.personalitybehaviorservice.repository.BehaviorRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BehaviorService {

    private final BehaviorRepository behaviorRepository;
    private final UserClient userClient;
    private final PersonalityEventPublisher eventPublisher;

    public BehaviorResponseDto create(BehaviorRequestDto request) {
        validateUserExists(request.getUserId());
        Behavior behavior = Behavior.builder()
                .agitationLevelPct(request.getAgitationLevelPct())
                .focusScorePct(request.getFocusScorePct())
                .engagementIndexPct(request.getEngagementIndexPct())
                .learningPacePercentile(request.getLearningPacePercentile())
                .fraudProbabilityScore(request.getFraudProbabilityScore())
                .lastInteraction(LocalDateTime.now())
                .userId(request.getUserId())
                .build();
        BehaviorResponseDto response = toDto(behaviorRepository.save(behavior));
        eventPublisher.publishBehaviorCreated(response);
        return response;
    }

    @Transactional(readOnly = true)
    public BehaviorResponseDto getById(String id) {
        Behavior behavior = behaviorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Behavior not found with ID: " + id));
        return toDto(behavior);
    }

    @Transactional(readOnly = true)
    public BehaviorResponseDto getByUserId(Long userId) {
        Behavior behavior = behaviorRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Behavior not found for user ID: " + userId));
        return toDto(behavior);
    }

    @Transactional(readOnly = true)
    public List<BehaviorResponseDto> getAll() {
        return behaviorRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public BehaviorResponseDto update(String id, BehaviorRequestDto request) {
        Behavior behavior = behaviorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Behavior not found with ID: " + id));

        if (request.getUserId() != null && !request.getUserId().equals(behavior.getUserId())) {
            validateUserExists(request.getUserId());
            behavior.setUserId(request.getUserId());
        }

        boolean changed = false;
        if (request.getAgitationLevelPct() != null) {
            behavior.setAgitationLevelPct(request.getAgitationLevelPct());
            changed = true;
        }
        if (request.getFocusScorePct() != null) {
            behavior.setFocusScorePct(request.getFocusScorePct());
            changed = true;
        }
        if (request.getEngagementIndexPct() != null) {
            behavior.setEngagementIndexPct(request.getEngagementIndexPct());
            changed = true;
        }
        if (request.getLearningPacePercentile() != null) {
            behavior.setLearningPacePercentile(request.getLearningPacePercentile());
            changed = true;
        }
        if (request.getFraudProbabilityScore() != null) {
            behavior.setFraudProbabilityScore(request.getFraudProbabilityScore());
            changed = true;
        }
        if (changed) {
            behavior.setLastInteraction(LocalDateTime.now());
        }

        BehaviorResponseDto response = toDto(behaviorRepository.save(behavior));
        eventPublisher.publishBehaviorUpdated(response);
        return response;
    }

    public void delete(String id) {
        if (!behaviorRepository.existsById(id)) {
            throw new RuntimeException("Behavior not found with ID: " + id);
        }
        behaviorRepository.deleteById(id);
    }

    private void validateUserExists(Long userId) {
        try {
            userClient.getUserById(userId);
        } catch (FeignException.NotFound ex) {
            throw new RuntimeException("User not found with ID: " + userId, ex);
        } catch (FeignException ex) {
            throw new RuntimeException("User service returned an error while validating user ID: " + userId, ex);
        } catch (Exception ex) {
            throw new RuntimeException("User service is unavailable while validating user ID: " + userId, ex);
        }
    }

    private BehaviorResponseDto toDto(Behavior behavior) {
        return BehaviorResponseDto.builder()
                .behaviorId(behavior.getId())
                .agitationLevelPct(behavior.getAgitationLevelPct())
                .focusScorePct(behavior.getFocusScorePct())
                .engagementIndexPct(behavior.getEngagementIndexPct())
                .learningPacePercentile(behavior.getLearningPacePercentile())
                .fraudProbabilityScore(behavior.getFraudProbabilityScore())
                .lastInteraction(behavior.getLastInteraction())
                .userId(behavior.getUserId())
                .build();
    }
}
