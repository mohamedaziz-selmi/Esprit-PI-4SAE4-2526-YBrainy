package com.esprit.userservice.service;

import com.esprit.userservice.dto.XpAwardResult;
import com.esprit.userservice.model.LevelConfig;
import com.esprit.userservice.model.User;
import com.esprit.userservice.model.UserXpEvent;
import com.esprit.userservice.model.XpSource;
import com.esprit.userservice.repository.UserRepository;
import com.esprit.userservice.repository.UserXpEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class XpService {

    private final UserRepository userRepository;
    private final UserXpEventRepository xpEventRepository;

    @Transactional
    public XpAwardResult awardXp(Long userId, XpSource source) {
        User user = userRepository.findById(userId).orElseGet(() -> {
            String suffix = String.valueOf(userId);
            userRepository.insertPlaceholderUser(
                    userId,
                    "user-" + suffix,
                    "user-" + suffix + "@forum.local"
            );
            return userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        });

        int amount = source.getAmount();
        int oldLevel = user.getLevel();

        user.setXp(user.getXp() + amount);

        int newLevel = LevelConfig.computeLevel(user.getXp());
        boolean leveledUp = newLevel > oldLevel;
        user.setLevel(newLevel);

        userRepository.save(user);

        UserXpEvent event = UserXpEvent.builder()
                .user(user)
                .sourceType(source)
                .amount(amount)
                .newTotal(user.getXp())
                .newLevel(newLevel)
                .build();
        xpEventRepository.save(event);

        log.info("Awarded {} XP to user {} for {}. New total: {}", amount, userId, source, user.getXp());

        return XpAwardResult.builder()
                .userId(userId)
                .xpAwarded(amount)
                .newXpTotal((int) Math.min(user.getXp(), Integer.MAX_VALUE))
                .newLevel(newLevel)
                .leveledUp(leveledUp)
                .build();
    }
}
