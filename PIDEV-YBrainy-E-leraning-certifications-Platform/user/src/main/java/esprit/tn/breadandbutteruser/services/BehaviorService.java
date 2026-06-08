package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.dto.BehaviorRequestDto;
import esprit.tn.breadandbutteruser.dto.BehaviorResponseDto;
import esprit.tn.breadandbutteruser.entities.Behavior;
import esprit.tn.breadandbutteruser.repositories.BehaviorRepository;
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

    public BehaviorResponseDto create(BehaviorRequestDto request) {
        Behavior behavior = Behavior.builder()
                .agitationLevelPct(request.getAgitationLevelPct())
                .focusScorePct(request.getFocusScorePct())
                .engagementIndexPct(request.getEngagementIndexPct())
                .learningPacePercentile(request.getLearningPacePercentile())
                .fraudProbabilityScore(request.getFraudProbabilityScore())
                .lastInteraction(LocalDateTime.now())
                .build();
        return toDto(behaviorRepository.save(behavior));
    }

    @Transactional(readOnly = true)
    public BehaviorResponseDto getById(Long id) {
        Behavior behavior = behaviorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Behavior not found with ID: " + id));
        return toDto(behavior);
    }

    @Transactional(readOnly = true)
    public List<BehaviorResponseDto> getAll() {
        return behaviorRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public BehaviorResponseDto update(Long id, BehaviorRequestDto request) {
        Behavior behavior = behaviorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Behavior not found with ID: " + id));

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

        return toDto(behaviorRepository.save(behavior));
    }

    public void delete(Long id) {
        if (!behaviorRepository.existsById(id)) {
            throw new RuntimeException("Behavior not found with ID: " + id);
        }
        behaviorRepository.deleteById(id);
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
