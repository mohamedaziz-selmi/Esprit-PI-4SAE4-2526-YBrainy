package tn.esprit.warningbanappealservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.warningbanappealservice.client.UserClient;
import tn.esprit.warningbanappealservice.dto.WarningRequestDto;
import tn.esprit.warningbanappealservice.dto.WarningResponseDto;
import tn.esprit.warningbanappealservice.entity.Warning;
import tn.esprit.warningbanappealservice.messaging.DomainEventPublisher;
import tn.esprit.warningbanappealservice.repository.WarningRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarningServiceTest {

    @Mock WarningRepository warningRepository;
    @Mock UserClient userClient;
    @Mock DomainEventPublisher eventPublisher;
    @InjectMocks WarningService service;

    private Warning sampleWarning(String id, Long userId) {
        return Warning.builder()
                .warningId(id)
                .userId(userId)
                .reason("Spam content")
                .severity("MEDIUM")
                .issuedBy("admin@ybrainy.com")
                .issuedDate(LocalDateTime.now())
                .build();
    }

    private WarningRequestDto sampleRequest(Long userId) {
        return WarningRequestDto.builder()
                .userId(userId)
                .reason("Spam content")
                .severity("MEDIUM")
                .issuedBy("admin@ybrainy.com")
                .build();
    }

    /* ─── create ─── */

    @Test
    @DisplayName("create() saves warning and publishes event")
    void create_success() {
        Warning saved = sampleWarning("w1", 1L);
        when(userClient.getUserById(1L)).thenReturn(null);
        when(warningRepository.save(any())).thenReturn(saved);
        doNothing().when(eventPublisher).publishWarningCreated("w1", 1L);

        WarningResponseDto result = service.create(sampleRequest(1L));

        assertThat(result.getWarningId()).isEqualTo("w1");
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getSeverity()).isEqualTo("MEDIUM");
        verify(warningRepository).save(any());
        verify(eventPublisher).publishWarningCreated("w1", 1L);
    }

    @Test
    @DisplayName("create() wraps exception when user service is unavailable")
    void create_userServiceDown_throws() {
        when(userClient.getUserById(99L)).thenThrow(new RuntimeException("Timeout"));

        assertThatThrownBy(() -> service.create(sampleRequest(99L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unavailable");
    }

    /* ─── getById ─── */

    @Test
    @DisplayName("getById() returns DTO when warning exists")
    void getById_found() {
        Warning w = sampleWarning("w2", 2L);
        when(warningRepository.findById("w2")).thenReturn(Optional.of(w));

        WarningResponseDto result = service.getById("w2");

        assertThat(result.getWarningId()).isEqualTo("w2");
        assertThat(result.getReason()).isEqualTo("Spam content");
    }

    @Test
    @DisplayName("getById() throws when warning not found")
    void getById_notFound_throws() {
        when(warningRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    /* ─── getAll ─── */

    @Test
    @DisplayName("getAll() returns all warnings")
    void getAll_returnsList() {
        when(warningRepository.findAll())
                .thenReturn(List.of(sampleWarning("w1", 1L), sampleWarning("w2", 2L)));

        List<WarningResponseDto> result = service.getAll();

        assertThat(result).hasSize(2);
    }

    /* ─── getByUserId ─── */

    @Test
    @DisplayName("getByUserId() returns warnings for user ordered by date")
    void getByUserId_returnsUserWarnings() {
        when(warningRepository.findByUserIdOrderByIssuedDateDesc(3L))
                .thenReturn(List.of(sampleWarning("w3", 3L)));

        List<WarningResponseDto> result = service.getByUserId(3L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(3L);
    }

    /* ─── update ─── */

    @Test
    @DisplayName("update() patches only non-null fields")
    void update_partialPatch() {
        Warning existing = sampleWarning("w4", 4L);
        when(warningRepository.findById("w4")).thenReturn(Optional.of(existing));
        when(warningRepository.save(any())).thenReturn(existing);
        doNothing().when(eventPublisher).publishWarningUpdated(any(), any());

        WarningRequestDto req = WarningRequestDto.builder()
                .severity("HIGH")
                .build();

        service.update("w4", req);

        assertThat(existing.getSeverity()).isEqualTo("HIGH");
        assertThat(existing.getReason()).isEqualTo("Spam content"); // unchanged
        verify(eventPublisher).publishWarningUpdated("w4", 4L);
    }

    @Test
    @DisplayName("update() throws when warning not found")
    void update_notFound_throws() {
        when(warningRepository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("gone", sampleRequest(1L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("update() validates new userId when it changes")
    void update_newUserId_validatesUser() {
        Warning existing = sampleWarning("w5", 5L);
        when(warningRepository.findById("w5")).thenReturn(Optional.of(existing));
        when(userClient.getUserById(10L)).thenReturn(null);
        when(warningRepository.save(any())).thenReturn(existing);
        doNothing().when(eventPublisher).publishWarningUpdated(any(), any());

        WarningRequestDto req = WarningRequestDto.builder()
                .userId(10L)
                .reason("New reason")
                .severity("LOW")
                .issuedBy("admin")
                .build();

        service.update("w5", req);

        verify(userClient).getUserById(10L);
        assertThat(existing.getUserId()).isEqualTo(10L);
    }

    /* ─── delete ─── */

    @Test
    @DisplayName("delete() removes warning and publishes event")
    void delete_success() {
        Warning w = sampleWarning("w6", 6L);
        when(warningRepository.findById("w6")).thenReturn(Optional.of(w));
        doNothing().when(warningRepository).deleteById("w6");
        doNothing().when(eventPublisher).publishWarningDeleted("w6", 6L);

        service.delete("w6");

        verify(warningRepository).deleteById("w6");
        verify(eventPublisher).publishWarningDeleted("w6", 6L);
    }

    @Test
    @DisplayName("delete() throws when warning not found")
    void delete_notFound_throws() {
        when(warningRepository.findById("none")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("none"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    /* ─── deleteByUserId ─── */

    @Test
    @DisplayName("deleteByUserId() delegates to repository")
    void deleteByUserId_callsRepo() {
        doNothing().when(warningRepository).deleteByUserId(7L);

        service.deleteByUserId(7L);

        verify(warningRepository).deleteByUserId(7L);
    }
}
