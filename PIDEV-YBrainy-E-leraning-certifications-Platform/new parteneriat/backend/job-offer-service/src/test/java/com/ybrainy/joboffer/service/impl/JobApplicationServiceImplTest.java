package com.ybrainy.joboffer.service.impl;

import com.ybrainy.joboffer.dto.JobApplicationRequest;
import com.ybrainy.joboffer.dto.JobApplicationResponse;
import com.ybrainy.joboffer.dto.JobApplicationUpdateRequest;
import com.ybrainy.joboffer.entity.ApplicationStatus;
import com.ybrainy.joboffer.entity.JobApplication;
import com.ybrainy.joboffer.entity.JobOffer;
import com.ybrainy.joboffer.exception.BusinessException;
import com.ybrainy.joboffer.exception.ResourceNotFoundException;
import com.ybrainy.joboffer.mapper.JobApplicationMapper;
import com.ybrainy.joboffer.messaging.JobApplicationEventPublisher;
import com.ybrainy.joboffer.repository.JobApplicationRepository;
import com.ybrainy.joboffer.repository.JobOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceImplTest {

    @Mock JobApplicationRepository applicationRepository;
    @Mock JobOfferRepository offerRepository;
    @Mock JobApplicationMapper mapper;
    @Mock JobApplicationEventPublisher eventPublisher;

    private JobApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobApplicationServiceImpl(applicationRepository, offerRepository, mapper, eventPublisher);
    }

    private JobOffer sampleOffer(String id, String title) {
        JobOffer o = new JobOffer();
        o.setId(id);
        o.setTitle(title);
        return o;
    }

    private JobApplication sampleApp(String id, String offerId, ApplicationStatus status) {
        JobApplication a = new JobApplication();
        a.setId(id);
        a.setOfferId(offerId);
        a.setApplicantName("Jane Doe");
        a.setApplicantEmail("jane@doe.com");
        a.setStatus(status);
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        return a;
    }

    private JobApplicationResponse sampleResponse(String id, String offerId, ApplicationStatus status) {
        return new JobApplicationResponse(id, offerId, "Jane Doe", "jane@doe.com",
                "Interested", null, status, null, Instant.now());
    }

    private JobApplicationRequest sampleRequest() {
        return new JobApplicationRequest("Jane Doe", "jane@doe.com", "Interested", null);
    }

    /* ─── create ─── */

    @Test
    @DisplayName("create() saves application and publishes event")
    void create_success() {
        JobOffer offer = sampleOffer("off-1", "Java Dev");
        JobApplication saved = sampleApp("app-1", "off-1", ApplicationStatus.PENDING);
        JobApplicationResponse response = sampleResponse("app-1", "off-1", ApplicationStatus.PENDING);

        when(offerRepository.findById("off-1")).thenReturn(Optional.of(offer));
        when(applicationRepository.existsByOfferIdAndApplicantEmailIgnoreCase("off-1", "jane@doe.com")).thenReturn(false);
        when(mapper.toEntity("off-1", sampleRequest())).thenReturn(saved);
        when(applicationRepository.save(saved)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);
        doNothing().when(eventPublisher).publishCreated(any(), any());

        JobApplicationResponse result = service.create("off-1", sampleRequest());

        assertThat(result.status()).isEqualTo(ApplicationStatus.PENDING);
        verify(applicationRepository).save(saved);
        verify(eventPublisher).publishCreated(saved, "Java Dev");
    }

    @Test
    @DisplayName("create() throws ResourceNotFoundException when offer not found")
    void create_offerNotFound_throws() {
        when(offerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("missing", sampleRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Job offer not found");
    }

    @Test
    @DisplayName("create() throws BusinessException when applicant already applied")
    void create_duplicateApplication_throws() {
        when(offerRepository.findById("off-2")).thenReturn(Optional.of(sampleOffer("off-2", "Python Dev")));
        when(applicationRepository.existsByOfferIdAndApplicantEmailIgnoreCase("off-2", "jane@doe.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create("off-2", sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already applied");

        verify(applicationRepository, never()).save(any());
    }

    /* ─── listByOffer ─── */

    @Test
    @DisplayName("listByOffer() returns applications for the given offer")
    void listByOffer_success() {
        JobApplication app = sampleApp("app-2", "off-3", ApplicationStatus.PENDING);
        JobApplicationResponse response = sampleResponse("app-2", "off-3", ApplicationStatus.PENDING);

        when(offerRepository.existsById("off-3")).thenReturn(true);
        when(applicationRepository.findByOfferIdOrderByCreatedAtDesc("off-3")).thenReturn(List.of(app));
        when(mapper.toResponse(app)).thenReturn(response);

        List<JobApplicationResponse> result = service.listByOffer("off-3");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).offerId()).isEqualTo("off-3");
    }

    @Test
    @DisplayName("listByOffer() throws ResourceNotFoundException when offer not found")
    void listByOffer_offerNotFound_throws() {
        when(offerRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.listByOffer("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Job offer not found");
    }

    /* ─── listAll ─── */

    @Test
    @DisplayName("listAll() returns all applications")
    void listAll_returnsList() {
        JobApplication app = sampleApp("app-3", "off-4", ApplicationStatus.PENDING);
        when(applicationRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(app));
        when(mapper.toResponse(app)).thenReturn(sampleResponse("app-3", "off-4", ApplicationStatus.PENDING));

        assertThat(service.listAll()).hasSize(1);
    }

    /* ─── updateReview ─── */

    @Test
    @DisplayName("updateReview() changes status and publishes event when status changes")
    void updateReview_statusChanges_publishesEvent() {
        JobApplication app = sampleApp("app-4", "off-5", ApplicationStatus.PENDING);
        JobApplicationUpdateRequest req = new JobApplicationUpdateRequest(ApplicationStatus.ACCEPTED, "Excellent");
        JobApplication saved = sampleApp("app-4", "off-5", ApplicationStatus.ACCEPTED);

        when(applicationRepository.findById("app-4")).thenReturn(Optional.of(app));
        doNothing().when(mapper).applyReview(app, req);
        when(applicationRepository.save(app)).thenReturn(saved);
        when(offerRepository.findById("off-5")).thenReturn(Optional.of(sampleOffer("off-5", "DevOps")));
        doNothing().when(eventPublisher).publishStatusChanged(any(), any());
        when(mapper.toResponse(saved)).thenReturn(sampleResponse("app-4", "off-5", ApplicationStatus.ACCEPTED));

        JobApplicationResponse result = service.updateReview("app-4", req);

        assertThat(result.status()).isEqualTo(ApplicationStatus.ACCEPTED);
        verify(eventPublisher).publishStatusChanged(saved, "DevOps");
    }

    @Test
    @DisplayName("updateReview() throws ResourceNotFoundException when application not found")
    void updateReview_notFound_throws() {
        when(applicationRepository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateReview("gone",
                new JobApplicationUpdateRequest(ApplicationStatus.REJECTED, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Job application not found");
    }

    /* ─── delete ─── */

    @Test
    @DisplayName("delete() removes application when found")
    void delete_success() {
        JobApplication app = sampleApp("app-5", "off-6", ApplicationStatus.PENDING);
        when(applicationRepository.findById("app-5")).thenReturn(Optional.of(app));
        doNothing().when(applicationRepository).delete(app);

        service.delete("app-5");

        verify(applicationRepository).delete(app);
    }

    @Test
    @DisplayName("delete() throws ResourceNotFoundException when application not found")
    void delete_notFound_throws() {
        when(applicationRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Job application not found");
    }
}
