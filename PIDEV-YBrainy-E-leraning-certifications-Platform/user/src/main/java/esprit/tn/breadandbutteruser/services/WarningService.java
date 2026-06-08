package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.dto.WarningRequestDto;
import esprit.tn.breadandbutteruser.dto.WarningResponseDto;
import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.Warning;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import esprit.tn.breadandbutteruser.repositories.WarningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class WarningService {

    private final WarningRepository warningRepository;
    private final UserRepository userRepository;

    public WarningResponseDto create(WarningRequestDto request, String issuedBy) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + request.getUserId()));

        Warning warning = Warning.builder()
                .reason(request.getReason())
                .severity(request.getSeverity())
                .issuedBy(issuedBy)
                .issuedDate(LocalDateTime.now())
                .user(user)
                .build();

        Warning saved = warningRepository.save(warning);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public WarningResponseDto getById(Long id) {
        Warning warning = warningRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warning not found with ID: " + id));
        return toDto(warning);
    }

    @Transactional(readOnly = true)
    public List<WarningResponseDto> getByUserId(Long userId) {
        return warningRepository.findByUserUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WarningResponseDto> getAll() {
        return warningRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public WarningResponseDto update(Long id, WarningRequestDto request) {
        Warning warning = warningRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warning not found with ID: " + id));
        if (request.getReason() != null) {
            warning.setReason(request.getReason());
        }
        if (request.getSeverity() != null) {
            warning.setSeverity(request.getSeverity());
        }
        return toDto(warningRepository.save(warning));
    }

    public void delete(Long id) {
        if (!warningRepository.existsById(id)) {
            throw new RuntimeException("Warning not found with ID: " + id);
        }
        warningRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long countByUserId(Long userId) {
        Long count = warningRepository.countByUserUserId(userId);
        return count != null ? count : 0L;
    }

    private WarningResponseDto toDto(Warning warning) {
        return WarningResponseDto.builder()
                .warningId(warning.getWarningId())
                .reason(warning.getReason())
                .severity(warning.getSeverity())
                .issuedDate(warning.getIssuedDate())
                .issuedBy(warning.getIssuedBy())
                .userId(warning.getUser() != null ? warning.getUser().getUserId() : null)
                .build();
    }
}
