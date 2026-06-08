package tn.esprit.personalitybehaviorservice.service;

import tn.esprit.personalitybehaviorservice.client.UserClient;
import tn.esprit.personalitybehaviorservice.dto.BehaviorRequestDto;
import tn.esprit.personalitybehaviorservice.dto.BehaviorResponseDto;
import tn.esprit.personalitybehaviorservice.dto.PersonalityRequestDto;
import tn.esprit.personalitybehaviorservice.dto.PersonalityResponseDto;
import tn.esprit.personalitybehaviorservice.entity.Behavior;
import tn.esprit.personalitybehaviorservice.entity.Personality;
import tn.esprit.personalitybehaviorservice.messaging.PersonalityEventPublisher;
import tn.esprit.personalitybehaviorservice.repository.BehaviorRepository;
import tn.esprit.personalitybehaviorservice.repository.PersonalityRepository;
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
public class PersonalityService {

    private final PersonalityRepository personalityRepository;
    private final BehaviorRepository behaviorRepository;
    private final UserClient userClient;
    private final PersonalityEventPublisher eventPublisher;

    public PersonalityResponseDto create(PersonalityRequestDto request) {
        validateUserExists(request.getUserId());
        if (personalityRepository.existsByUserId(request.getUserId())) {
            throw new RuntimeException("Personality already exists for user ID: " + request.getUserId());
        }

        Personality personality = Personality.builder()
                .visualLearningPct(request.getVisualLearningPct())
                .auditoryLearningPct(request.getAuditoryLearningPct())
                .kinestheticLearningPct(request.getKinestheticLearningPct())
                .careerAlignmentScore(request.getCareerAlignmentScore())
                .cognitiveLoadTolerance(request.getCognitiveLoadTolerance())
                .userId(request.getUserId())
                .build();
        if (request.getCareerGoals() != null) {
            personality.setCareerGoals(request.getCareerGoals());
        }

        if (request.getBehavior() != null) {
            BehaviorRequestDto b = request.getBehavior();
            Behavior behavior = Behavior.builder()
                    .agitationLevelPct(b.getAgitationLevelPct())
                    .focusScorePct(b.getFocusScorePct())
                    .engagementIndexPct(b.getEngagementIndexPct())
                    .learningPacePercentile(b.getLearningPacePercentile())
                    .fraudProbabilityScore(b.getFraudProbabilityScore())
                    .lastInteraction(LocalDateTime.now())
                    .userId(request.getUserId())
                    .build();
            personality.setBehavior(behavior);
        }

        PersonalityResponseDto response = toDto(personalityRepository.save(personality));
        eventPublisher.publishPersonalityCreated(response);
        return response;
    }

    @Transactional(readOnly = true)
    public PersonalityResponseDto getById(String id) {
        Personality personality = personalityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Personality not found with ID: " + id));
        return toDto(personality);
    }

    @Transactional(readOnly = true)
    public PersonalityResponseDto getByUserId(Long userId) {
        Personality personality = personalityRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Personality not found for user ID: " + userId));
        return toDto(personality);
    }

    @Transactional(readOnly = true)
    public List<PersonalityResponseDto> getAll() {
        return personalityRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public PersonalityResponseDto update(String id, PersonalityRequestDto request) {
        Personality personality = personalityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Personality not found with ID: " + id));

        if (request.getVisualLearningPct() != null)
            personality.setVisualLearningPct(request.getVisualLearningPct());
        if (request.getAuditoryLearningPct() != null)
            personality.setAuditoryLearningPct(request.getAuditoryLearningPct());
        if (request.getKinestheticLearningPct() != null)
            personality.setKinestheticLearningPct(request.getKinestheticLearningPct());
        if (request.getCareerAlignmentScore() != null)
            personality.setCareerAlignmentScore(request.getCareerAlignmentScore());
        if (request.getCognitiveLoadTolerance() != null)
            personality.setCognitiveLoadTolerance(request.getCognitiveLoadTolerance());
        if (request.getCareerGoals() != null)
            personality.setCareerGoals(request.getCareerGoals());

        if (request.getBehavior() != null) {
            if (personality.getBehavior() == null) {
                personality.setBehavior(Behavior.builder().build());
            }
            Behavior behavior = personality.getBehavior();
            BehaviorRequestDto b = request.getBehavior();
            boolean behaviorChanged = false;
            if (b.getAgitationLevelPct() != null) {
                behavior.setAgitationLevelPct(b.getAgitationLevelPct());
                behaviorChanged = true;
            }
            if (b.getFocusScorePct() != null) {
                behavior.setFocusScorePct(b.getFocusScorePct());
                behaviorChanged = true;
            }
            if (b.getEngagementIndexPct() != null) {
                behavior.setEngagementIndexPct(b.getEngagementIndexPct());
                behaviorChanged = true;
            }
            if (b.getLearningPacePercentile() != null) {
                behavior.setLearningPacePercentile(b.getLearningPacePercentile());
                behaviorChanged = true;
            }
            if (b.getFraudProbabilityScore() != null) {
                behavior.setFraudProbabilityScore(b.getFraudProbabilityScore());
                behaviorChanged = true;
            }
            if (behaviorChanged) {
                behavior.setLastInteraction(LocalDateTime.now());
            }
        }

        PersonalityResponseDto response = toDto(personalityRepository.save(personality));
        eventPublisher.publishPersonalityUpdated(response);
        return response;
    }

    public void delete(String id) {
        if (!personalityRepository.existsById(id)) {
            throw new RuntimeException("Personality not found with ID: " + id);
        }
        personalityRepository.deleteById(id);
    }

    public void deleteByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        personalityRepository.deleteByUserId(userId);
        behaviorRepository.deleteByUserId(userId);
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

    private PersonalityResponseDto toDto(Personality personality) {
        return PersonalityResponseDto.builder()
                .personalityId(personality.getId())
                .visualLearningPct(personality.getVisualLearningPct())
                .auditoryLearningPct(personality.getAuditoryLearningPct())
                .kinestheticLearningPct(personality.getKinestheticLearningPct())
                .careerAlignmentScore(personality.getCareerAlignmentScore())
                .cognitiveLoadTolerance(personality.getCognitiveLoadTolerance())
                .careerGoals(personality.getCareerGoals())
                .behavior(personality.getBehavior() != null ? toDto(personality.getBehavior()) : null)
                .userId(personality.getUserId())
                .build();
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
