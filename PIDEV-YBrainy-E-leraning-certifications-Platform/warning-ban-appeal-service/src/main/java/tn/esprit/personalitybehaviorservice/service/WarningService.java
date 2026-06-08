package tn.esprit.warningbanappealservice.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.warningbanappealservice.client.UserClient;
import tn.esprit.warningbanappealservice.dto.WarningRequestDto;
import tn.esprit.warningbanappealservice.dto.WarningResponseDto;
import tn.esprit.warningbanappealservice.entity.Warning;
import tn.esprit.warningbanappealservice.messaging.DomainEventPublisher;
import tn.esprit.warningbanappealservice.repository.WarningRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WarningService {

    private final WarningRepository warningRepository;
    private final UserClient userClient;
    private final DomainEventPublisher eventPublisher;

    public WarningResponseDto create(WarningRequestDto request) {
        validateUserExists(request.getUserId());
        Warning warning = Warning.builder()
                .userId(request.getUserId())
                .reason(request.getReason())
                .severity(request.getSeverity())
                .issuedBy(request.getIssuedBy())
                .issuedDate(LocalDateTime.now())
                .build();
        Warning saved = warningRepository.save(warning);
        eventPublisher.publishWarningCreated(saved.getWarningId(), saved.getUserId());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public WarningResponseDto getById(String id) {
        return toDto(warningRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warning not found with ID: " + id)));
    }

    @Transactional(readOnly = true)
    public List<WarningResponseDto> getAll() {
        return warningRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<WarningResponseDto> getByUserId(Long userId) {
        return warningRepository.findByUserIdOrderByIssuedDateDesc(userId).stream().map(this::toDto).toList();
    }

    public WarningResponseDto update(String id, WarningRequestDto request) {
        Warning warning = warningRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warning not found with ID: " + id));

        if (request.getUserId() != null && !request.getUserId().equals(warning.getUserId())) {
            validateUserExists(request.getUserId());
            warning.setUserId(request.getUserId());
        }
        if (request.getReason() != null) {
            warning.setReason(request.getReason());
        }
        if (request.getSeverity() != null) {
            warning.setSeverity(request.getSeverity());
        }
        if (request.getIssuedBy() != null) {
            warning.setIssuedBy(request.getIssuedBy());
        }

        Warning saved = warningRepository.save(warning);
        eventPublisher.publishWarningUpdated(saved.getWarningId(), saved.getUserId());
        return toDto(saved);
    }

    public void delete(String id) {
        Warning warning = warningRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warning not found with ID: " + id));
        warningRepository.deleteById(id);
        eventPublisher.publishWarningDeleted(warning.getWarningId(), warning.getUserId());
    }

    public void deleteByUserId(Long userId) {
        warningRepository.deleteByUserId(userId);
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

    private WarningResponseDto toDto(Warning warning) {
        return WarningResponseDto.builder()
                .warningId(warning.getWarningId())
                .userId(warning.getUserId())
                .reason(warning.getReason())
                .severity(warning.getSeverity())
                .issuedBy(warning.getIssuedBy())
                .issuedDate(warning.getIssuedDate())
                .build();
    }
}
