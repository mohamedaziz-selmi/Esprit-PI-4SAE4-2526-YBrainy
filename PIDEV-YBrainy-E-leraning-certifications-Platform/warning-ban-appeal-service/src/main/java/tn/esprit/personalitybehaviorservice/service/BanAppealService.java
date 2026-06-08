package tn.esprit.warningbanappealservice.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.warningbanappealservice.client.UserClient;
import tn.esprit.warningbanappealservice.dto.BanAppealRequestDto;
import tn.esprit.warningbanappealservice.dto.BanAppealResponseDto;
import tn.esprit.warningbanappealservice.entity.BanAppeal;
import tn.esprit.warningbanappealservice.messaging.DomainEventPublisher;
import tn.esprit.warningbanappealservice.repository.BanAppealRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BanAppealService {

    private final BanAppealRepository banAppealRepository;
    private final UserClient userClient;
    private final DomainEventPublisher eventPublisher;

    public BanAppealResponseDto create(BanAppealRequestDto request) {
        validateUserExists(request.getUserId());
        BanAppeal appeal = BanAppeal.builder()
                .userId(request.getUserId())
                .description(request.getDescription())
                .appealStatus(request.getAppealStatus() == null ? "PENDING" : request.getAppealStatus())
                .submittedDate(LocalDateTime.now())
                .viewed(Boolean.TRUE.equals(request.getViewed()))
                .reviewedBy(request.getReviewedBy())
                .viewedAt(Boolean.TRUE.equals(request.getViewed()) ? LocalDateTime.now() : null)
                .viewedBy(Boolean.TRUE.equals(request.getViewed()) ? request.getReviewedBy() : null)
                .build();
        BanAppeal saved = banAppealRepository.save(appeal);
        eventPublisher.publishAppealCreated(saved.getAppealId(), saved.getUserId());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public BanAppealResponseDto getById(String id) {
        return toDto(banAppealRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ban appeal not found with ID: " + id)));
    }

    @Transactional(readOnly = true)
    public List<BanAppealResponseDto> getAll() {
        return banAppealRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BanAppealResponseDto> getByUserId(Long userId) {
        return banAppealRepository.findByUserIdOrderBySubmittedDateDesc(userId).stream().map(this::toDto).toList();
    }

    public BanAppealResponseDto update(String id, BanAppealRequestDto request) {
        BanAppeal appeal = banAppealRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ban appeal not found with ID: " + id));

        if (request.getUserId() != null && !request.getUserId().equals(appeal.getUserId())) {
            validateUserExists(request.getUserId());
            appeal.setUserId(request.getUserId());
        }
        if (request.getDescription() != null) {
            appeal.setDescription(request.getDescription());
        }
        if (request.getAppealStatus() != null) {
            appeal.setAppealStatus(request.getAppealStatus());
            if (!"PENDING".equalsIgnoreCase(request.getAppealStatus())) {
                appeal.setResolvedDate(LocalDateTime.now());
            }
        }
        if (request.getReviewedBy() != null) {
            appeal.setReviewedBy(request.getReviewedBy());
        }
        if (request.getViewed() != null) {
            appeal.setViewed(request.getViewed());
            appeal.setViewedAt(request.getViewed() ? LocalDateTime.now() : null);
            appeal.setViewedBy(request.getViewed() ? request.getReviewedBy() : null);
        }

        BanAppeal saved = banAppealRepository.save(appeal);
        eventPublisher.publishAppealUpdated(saved.getAppealId(), saved.getUserId());
        return toDto(saved);
    }

    public void delete(String id) {
        BanAppeal appeal = banAppealRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ban appeal not found with ID: " + id));
        banAppealRepository.deleteById(id);
        eventPublisher.publishAppealDeleted(appeal.getAppealId(), appeal.getUserId());
    }

    public void deleteByUserId(Long userId) {
        banAppealRepository.deleteByUserId(userId);
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

    private BanAppealResponseDto toDto(BanAppeal appeal) {
        return BanAppealResponseDto.builder()
                .appealId(appeal.getAppealId())
                .userId(appeal.getUserId())
                .description(appeal.getDescription())
                .appealStatus(appeal.getAppealStatus())
                .submittedDate(appeal.getSubmittedDate())
                .resolvedDate(appeal.getResolvedDate())
                .reviewedBy(appeal.getReviewedBy())
                .viewed(appeal.isViewed())
                .viewedAt(appeal.getViewedAt())
                .viewedBy(appeal.getViewedBy())
                .build();
    }
}
