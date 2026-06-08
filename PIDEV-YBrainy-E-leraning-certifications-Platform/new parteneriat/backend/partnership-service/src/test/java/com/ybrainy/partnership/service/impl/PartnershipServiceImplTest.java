package com.ybrainy.partnership.service.impl;

import com.ybrainy.partnership.dto.PartnershipRequest;
import com.ybrainy.partnership.dto.PartnershipResponse;
import com.ybrainy.partnership.entity.Partnership;
import com.ybrainy.partnership.exception.BusinessException;
import com.ybrainy.partnership.exception.ResourceNotFoundException;
import com.ybrainy.partnership.mapper.PartnershipMapper;
import com.ybrainy.partnership.messaging.PartnershipEventPublisher;
import com.ybrainy.partnership.repository.PartnershipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnershipServiceImplTest {

    @Mock PartnershipRepository repository;
    @Mock PartnershipMapper mapper;
    @Mock PartnershipEventPublisher eventPublisher;
    @InjectMocks PartnershipServiceImpl service;

    private PartnershipRequest sampleRequest(String name, String email) {
        return new PartnershipRequest(name, email, "+216123456", "http://partner.io",
                "A great partner", true);
    }

    private Partnership samplePartnership(String id, String name, String email) {
        Partnership p = new Partnership();
        p.setId(id);
        p.setName(name);
        p.setEmail(email);
        p.setActive(true);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }

    private PartnershipResponse sampleResponse(String id, String name, String email) {
        return new PartnershipResponse(id, name, email, "+216123456",
                "http://partner.io", "A great partner", true, Instant.now(), Instant.now());
    }

    /* ─── create ─── */

    @Test
    @DisplayName("create() saves partnership and publishes event")
    void create_success() {
        PartnershipRequest req = sampleRequest("Acme Corp", "acme@corp.com");
        Partnership entity = samplePartnership("p1", "Acme Corp", "acme@corp.com");
        PartnershipResponse response = sampleResponse("p1", "Acme Corp", "acme@corp.com");

        when(repository.existsByEmailIgnoreCase("acme@corp.com")).thenReturn(false);
        when(mapper.toEntity(req)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toResponse(entity)).thenReturn(response);
        doNothing().when(eventPublisher).publishCreated(entity);

        PartnershipResponse result = service.create(req);

        assertThat(result.name()).isEqualTo("Acme Corp");
        verify(repository).save(entity);
        verify(eventPublisher).publishCreated(entity);
    }

    @Test
    @DisplayName("create() throws BusinessException when email already exists")
    void create_duplicateEmail_throws() {
        PartnershipRequest req = sampleRequest("Acme Corp", "Acme@Corp.COM");
        when(repository.existsByEmailIgnoreCase("acme@corp.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        verify(repository, never()).save(any());
    }

    /* ─── update ─── */

    @Test
    @DisplayName("update() modifies entity and publishes event")
    void update_success() {
        PartnershipRequest req = sampleRequest("Updated Name", "updated@corp.com");
        Partnership existing = samplePartnership("p2", "Old Name", "old@corp.com");
        PartnershipResponse response = sampleResponse("p2", "Updated Name", "updated@corp.com");

        when(repository.findById("p2")).thenReturn(Optional.of(existing));
        when(repository.existsByEmailIgnoreCaseAndIdNot("updated@corp.com", "p2")).thenReturn(false);
        doNothing().when(mapper).apply(existing, req);
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(response);
        doNothing().when(eventPublisher).publishUpdated(existing);

        PartnershipResponse result = service.update("p2", req);

        assertThat(result.name()).isEqualTo("Updated Name");
        verify(repository).save(existing);
        verify(eventPublisher).publishUpdated(existing);
    }

    @Test
    @DisplayName("update() throws BusinessException when email is taken by another partnership")
    void update_emailConflict_throws() {
        PartnershipRequest req = sampleRequest("Acme2", "Taken@Corp.COM");
        Partnership existing = samplePartnership("p3", "Acme2", "old@corp.com");

        when(repository.findById("p3")).thenReturn(Optional.of(existing));
        when(repository.existsByEmailIgnoreCaseAndIdNot("taken@corp.com", "p3")).thenReturn(true);

        assertThatThrownBy(() -> service.update("p3", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Another partnership");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update() throws ResourceNotFoundException when partnership not found")
    void update_notFound_throws() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", sampleRequest("X", "x@x.com")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Partnership not found");
    }

    /* ─── getById ─── */

    @Test
    @DisplayName("getById() returns DTO when partnership exists")
    void getById_found() {
        Partnership entity = samplePartnership("p4", "Tech Inc", "tech@inc.com");
        PartnershipResponse response = sampleResponse("p4", "Tech Inc", "tech@inc.com");

        when(repository.findById("p4")).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(response);

        PartnershipResponse result = service.getById("p4");

        assertThat(result.id()).isEqualTo("p4");
    }

    @Test
    @DisplayName("getById() throws ResourceNotFoundException when not found")
    void getById_notFound_throws() {
        when(repository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("gone"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Partnership not found");
    }

    /* ─── getAll ─── */

    @Test
    @DisplayName("getAll() without search returns all partnerships")
    void getAll_noSearch_returnsAll() {
        Partnership p = samplePartnership("p5", "FirmA", "firma@x.com");
        PartnershipResponse r = sampleResponse("p5", "FirmA", "firma@x.com");

        when(repository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(p)));
        when(mapper.toResponse(p)).thenReturn(r);

        Page<PartnershipResponse> page = service.getAll(null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAll() with search uses name-containing query")
    void getAll_withSearch_filtersResults() {
        Partnership p = samplePartnership("p6", "Acme Labs", "acmelabs@x.com");
        PartnershipResponse r = sampleResponse("p6", "Acme Labs", "acmelabs@x.com");

        when(repository.findByNameContainingIgnoreCase(eq("acme"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(p)));
        when(mapper.toResponse(p)).thenReturn(r);

        Page<PartnershipResponse> page = service.getAll("acme", PageRequest.of(0, 10));

        assertThat(page.getContent().get(0).name()).isEqualTo("Acme Labs");
        verify(repository, never()).findAll(any(PageRequest.class));
    }

    /* ─── delete ─── */

    @Test
    @DisplayName("delete() removes entity and publishes event")
    void delete_success() {
        Partnership entity = samplePartnership("p7", "GoAway Corp", "gone@corp.com");
        when(repository.findById("p7")).thenReturn(Optional.of(entity));
        doNothing().when(repository).delete(entity);
        doNothing().when(eventPublisher).publishDeleted(entity);

        service.delete("p7");

        verify(repository).delete(entity);
        verify(eventPublisher).publishDeleted(entity);
    }

    @Test
    @DisplayName("delete() throws ResourceNotFoundException when partnership not found")
    void delete_notFound_throws() {
        when(repository.findById("none")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("none"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Partnership not found");
    }

    /* ─── existsById ─── */

    @Test
    @DisplayName("existsById() delegates to repository")
    void existsById_delegates() {
        when(repository.existsById("p8")).thenReturn(true);

        assertThat(service.existsById("p8")).isTrue();
    }
}
