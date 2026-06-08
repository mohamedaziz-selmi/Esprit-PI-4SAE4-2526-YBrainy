package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.entities.Behavior;
import esprit.tn.breadandbutteruser.entities.InteractionEvent;
import esprit.tn.breadandbutteruser.entities.Personality;
import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.repositories.InteractionEventRepository;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BehaviorAnalysisScheduler {

    private final InteractionEventRepository eventRepo;
    private final UserRepository userRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void analyzeRecentEvents() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(5);
        List<String> subjects = eventRepo.findDistinctSubjectsSince(since);

        for (String subject : subjects) {
            List<InteractionEvent> events = eventRepo.findByKeycloakSubjectAndReceivedAtAfter(subject, since);

            String email = events.stream()
                    .map(InteractionEvent::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst()
                    .orElse(null);

            if (email == null) continue;

            User user = userRepository.findByKeycloakUserId(subject)
                    .or(() -> userRepository.findByEmail(email))
                    .orElse(null);
            if (user == null) continue;

            if (user.getPersonality() == null) {
                user.setPersonality(Personality.builder().build());
            }

            Personality p = user.getPersonality();
            if (p.getBehavior() == null) {
                p.setBehavior(Behavior.builder().build());
            }

            Behavior b = p.getBehavior();

            long totalMs = events.stream()
                    .filter(e -> e.getDurationMs() != null)
                    .mapToLong(InteractionEvent::getDurationMs)
                    .sum();

            long engagementMs = events.stream()
                    .filter(e -> "ENGAGEMENT".equalsIgnoreCase(e.getEventType()))
                    .mapToLong(e -> e.getDurationMs() != null ? e.getDurationMs() : 0)
                    .sum();

            double engagementPct = totalMs > 0 ? Math.min(100.0, (double) engagementMs / (double) totalMs * 100.0) : 0.0;

            double alpha = 0.3;
            Double oldEngagement = b.getEngagementIndexPct() != null ? b.getEngagementIndexPct() : 0.0;
            b.setEngagementIndexPct(alpha * engagementPct + (1 - alpha) * oldEngagement);
            b.setLastInteraction(LocalDateTime.now());

            userRepository.save(user);
        }
    }
}
