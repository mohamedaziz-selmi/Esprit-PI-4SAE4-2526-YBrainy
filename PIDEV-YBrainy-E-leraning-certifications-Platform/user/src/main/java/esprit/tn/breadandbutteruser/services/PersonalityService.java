package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.dto.BehaviorRequestDto;
import esprit.tn.breadandbutteruser.dto.BehaviorResponseDto;
import esprit.tn.breadandbutteruser.dto.PersonalityRequestDto;
import esprit.tn.breadandbutteruser.dto.PersonalityResponseDto;
import esprit.tn.breadandbutteruser.entities.Behavior;
import esprit.tn.breadandbutteruser.entities.Personality;
import esprit.tn.breadandbutteruser.repositories.PersonalityRepository;
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

    public PersonalityResponseDto create(PersonalityRequestDto request) {
        Personality personality = Personality.builder()
                .visualLearningPct(request.getVisualLearningPct())
                .auditoryLearningPct(request.getAuditoryLearningPct())
                .kinestheticLearningPct(request.getKinestheticLearningPct())
                .careerAlignmentScore(request.getCareerAlignmentScore())
                .cognitiveLoadTolerance(request.getCognitiveLoadTolerance())
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
                    .build();
            personality.setBehavior(behavior);
        }

        return toDto(personalityRepository.save(personality));
    }

    @Transactional(readOnly = true)
    public PersonalityResponseDto getById(Long id) {
        Personality personality = personalityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Personality not found with ID: " + id));
        return toDto(personality);
    }

    @Transactional(readOnly = true)
    public List<PersonalityResponseDto> getAll() {
        return personalityRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public PersonalityResponseDto update(Long id, PersonalityRequestDto request) {
        Personality personality = personalityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Personality not found with ID: " + id));

        if (request.getVisualLearningPct() != null) personality.setVisualLearningPct(request.getVisualLearningPct());
        if (request.getAuditoryLearningPct() != null) personality.setAuditoryLearningPct(request.getAuditoryLearningPct());
        if (request.getKinestheticLearningPct() != null) personality.setKinestheticLearningPct(request.getKinestheticLearningPct());
        if (request.getCareerAlignmentScore() != null) personality.setCareerAlignmentScore(request.getCareerAlignmentScore());
        if (request.getCognitiveLoadTolerance() != null) personality.setCognitiveLoadTolerance(request.getCognitiveLoadTolerance());
        if (request.getCareerGoals() != null) personality.setCareerGoals(request.getCareerGoals());

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

        return toDto(personalityRepository.save(personality));
    }

    public void delete(Long id) {
        if (!personalityRepository.existsById(id)) {
            throw new RuntimeException("Personality not found with ID: " + id);
        }
        personalityRepository.deleteById(id);
    }

    private PersonalityResponseDto toDto(Personality personality) {
        return PersonalityResponseDto.builder()
                .personalityId(personality.getPersonalityId())
                .visualLearningPct(personality.getVisualLearningPct())
                .auditoryLearningPct(personality.getAuditoryLearningPct())
                .kinestheticLearningPct(personality.getKinestheticLearningPct())
                .careerAlignmentScore(personality.getCareerAlignmentScore())
                .cognitiveLoadTolerance(personality.getCognitiveLoadTolerance())
                .careerGoals(personality.getCareerGoals())
                .behavior(personality.getBehavior() != null ? toDto(personality.getBehavior()) : null)
                .build();
    }

    private BehaviorResponseDto toDto(Behavior behavior) {
        return BehaviorResponseDto.builder()
                .behaviorId(behavior.getBehaviorId())
                .agitationLevelPct(behavior.getAgitationLevelPct())
                .focusScorePct(behavior.getFocusScorePct())
                .engagementIndexPct(behavior.getEngagementIndexPct())
                .learningPacePercentile(behavior.getLearningPacePercentile())
                .fraudProbabilityScore(behavior.getFraudProbabilityScore())
                .lastInteraction(behavior.getLastInteraction())
                .build();
    }
}
