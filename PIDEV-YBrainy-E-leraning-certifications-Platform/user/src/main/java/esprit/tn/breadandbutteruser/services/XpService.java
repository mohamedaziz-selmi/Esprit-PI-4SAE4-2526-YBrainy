package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.dto.XpEventDto;
import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.XpEvent;
import esprit.tn.breadandbutteruser.messaging.UserEventPublisher;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import esprit.tn.breadandbutteruser.repositories.XpEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class XpService {

    private final UserRepository userRepository;
    private final XpEventRepository xpEventRepository;
    private final UserEventPublisher userEventPublisher;

    public XpEventDto awardXp(Long userId, int amount, XpEvent.XpSourceType sourceType, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        int oldLevel = user.getLevel();
        long newXp = user.getXp() + amount;
        int newLevel = calculateLevel(newXp);
        boolean levelUp = newLevel > oldLevel;

        // Update user
        user.setXp(newXp);
        user.setLevel(newLevel);
        userRepository.save(user);

        // Create XP event
        XpEvent event = XpEvent.builder()
                .user(user)
                .sourceType(sourceType)
                .amount(amount)
                .newTotal(newXp)
                .newLevel(newLevel)
                .levelUp(levelUp)
                .description(description)
                .build();

        XpEvent saved = xpEventRepository.save(event);
        XpEventDto savedDto = XpEventDto.fromEntity(saved);
        userEventPublisher.publishXpAwarded(user, savedDto);

        if (levelUp) {
            log.info("User {} leveled up! {} -> {}", user.getUsername(), oldLevel, newLevel);
            userEventPublisher.publishLevelUp(user, savedDto);
        }

        return savedDto;
    }

    public List<XpEventDto> getRecentXpEvents(Long userId) {
        return xpEventRepository.findTop10ByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(XpEventDto::fromEntity)
                .collect(Collectors.toList());
    }

    public List<XpEventDto> getXpHistory(Long userId) {
        return xpEventRepository.findByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(XpEventDto::fromEntity)
                .collect(Collectors.toList());
    }

    public int calculateLevel(long xp) {
        if (xp < 100) return 1;
        return (int) (Math.log(xp / 100.0) / Math.log(2)) + 2;
    }

    public long getXpForNextLevel(int currentLevel) {
        if (currentLevel >= 50) return -1;
        return (long) (100 * Math.pow(2, currentLevel - 1));
    }

    public long getMinXpForLevel(int level) {
        if (level <= 1) return 0;
        return (long) (100 * Math.pow(2, level - 2));
    }

    public void updateStreak(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Simple streak logic - increment by 1
        user.setStreakDays(user.getStreakDays() + 1);
        userRepository.save(user);
    }
}
