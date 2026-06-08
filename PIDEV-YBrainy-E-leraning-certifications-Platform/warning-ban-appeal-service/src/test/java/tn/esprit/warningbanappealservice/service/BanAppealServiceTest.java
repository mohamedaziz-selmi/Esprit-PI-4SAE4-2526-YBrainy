package tn.esprit.warningbanappealservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.warningbanappealservice.client.UserClient;
import tn.esprit.warningbanappealservice.dto.BanAppealRequestDto;
import tn.esprit.warningbanappealservice.dto.BanAppealResponseDto;
import tn.esprit.warningbanappealservice.entity.BanAppeal;
import tn.esprit.warningbanappealservice.messaging.DomainEventPublisher;
import tn.esprit.warningbanappealservice.repository.BanAppealRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BanAppealServiceTest {

    @Mock BanAppealRepository banAppealRepository;
    @Mock UserClient userClient;
    @Mock DomainEventPublisher eventPublisher;
    @InjectMocks BanAppealService service;

    private BanAppeal sampleAppeal(String id, Long userId) {
        return BanAppeal.builder()
                .appealId(id)
                .userId(userId)
                .description("I was banned unfairly")
                .appealStatus("PENDING")
                .submittedDate(LocalDateTime.now())
                .build();
    }

    private BanAppealRequestDto sampleRequest(Long userId) {
        return BanAppealRequestDto.builder()
                .userId(userId)
                .description("I was banned unfairly")
                .build();
    }

    /* ─── create ─── */

    @Test
    @DisplayName("create() saves appeal with PENDING default status")
    void create_defaultStatusPending() {
        BanAppeal saved = sampleAppeal("a1", 1L);
        when(userClient.getUserById(1L)).thenReturn(null);
        when(banAppealRepository.save(any())).thenReturn(saved);
        doNothing().when(eventPublisher).publishAppealCreated("a1", 1L);

        BanAppealResponseDto result = service.create(sampleRequest(1L));

        assertThat(result.getAppealStatus()).isEqualTo("PENDING");
        assertThat(result.getUserId()).isEqualTo(1L);
        verify(banAppealRepository).save(any());
        verify(eventPublisher).publishAppealCreated("a1", 1L);
    }

    @Test
    @DisplayName("create() respects explicit appealStatus when provided")
    void create_explicitStatus() {
        BanAppeal saved = BanAppeal.builder()
                .appealId("a2").userId(2L).appealStatus("REVIEWED")
                .submittedDate(LocalDateTime.now()).build();

        when(userClient.getUserById(2L)).thenReturn(null);
        when(banAppealRepository.save(any())).thenReturn(saved);
        doNothing().when(eventPublisher).publishAppealCreated("a2", 2L);

        BanAppealRequestDto req = BanAppealRequestDto.builder()
                .userId(2L).description("Appeal").appealStatus("REVIEWED").build();

        BanAppealResponseDto result = service.create(req);

        assertThat(result.getAppealStatus()).isEqualTo("REVIEWED");
    }

    @Test
    @DisplayName("create() throws when user service is unavailable")
    void create_userServiceDown_throws() {
        when(userClient.getUserById(99L)).thenThrow(new RuntimeException("Down"));

        assertThatThrownBy(() -> service.create(sampleRequest(99L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unavailable");
    }

    /* ─── getById ─── */

    @Test
    @DisplayName("getById() returns DTO when appeal exists")
    void getById_found() {
        when(banAppealRepository.findById("a1")).thenReturn(Optional.of(sampleAppeal("a1", 3L)));

        BanAppealResponseDto result = service.getById("a1");

        assertThat(result.getAppealId()).isEqualTo("a1");
        assertThat(result.getDescription()).isEqualTo("I was banned unfairly");
    }

    @Test
    @DisplayName("getById() throws when appeal not found")
    void getById_notFound_throws() {
        when(banAppealRepository.findById("none")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("none"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    /* ─── getAll / getByUserId ─── */

    @Test
    @DisplayName("getAll() returns all appeals")
    void getAll_returnsList() {
        when(banAppealRepository.findAll())
                .thenReturn(List.of(sampleAppeal("a1", 1L), sampleAppeal("a2", 2L)));

        assertThat(service.getAll()).hasSize(2);
    }

    @Test
    @DisplayName("getByUserId() returns appeals for the given user")
    void getByUserId_returnsFiltered() {
        when(banAppealRepository.findByUserIdOrderBySubmittedDateDesc(5L))
                .thenReturn(List.of(sampleAppeal("a3", 5L)));

        List<BanAppealResponseDto> result = service.getByUserId(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(5L);
    }

    /* ─── update ─── */

    @Test
    @DisplayName("update() sets resolvedDate when status changes away from PENDING")
    void update_statusChange_setsResolvedDate() {
        BanAppeal existing = sampleAppeal("a4", 4L);
        when(banAppealRepository.findById("a4")).thenReturn(Optional.of(existing));
        when(banAppealRepository.save(any())).thenReturn(existing);
        doNothing().when(eventPublisher).publishAppealUpdated(any(), any());

        BanAppealRequestDto req = BanAppealRequestDto.builder()
                .appealStatus("APPROVED").build();

        service.update("a4", req);

        assertThat(existing.getAppealStatus()).isEqualTo("APPROVED");
        assertThat(existing.getResolvedDate()).isNotNull();
    }

    @Test
    @DisplayName("update() does not set resolvedDate when status remains PENDING")
    void update_statusStillPending_noResolvedDate() {
        BanAppeal existing = sampleAppeal("a5", 5L);
        when(banAppealRepository.findById("a5")).thenReturn(Optional.of(existing));
        when(banAppealRepository.save(any())).thenReturn(existing);
        doNothing().when(eventPublisher).publishAppealUpdated(any(), any());

        BanAppealRequestDto req = BanAppealRequestDto.builder()
                .appealStatus("PENDING").build();

        service.update("a5", req);

        assertThat(existing.getResolvedDate()).isNull();
    }

    @Test
    @DisplayName("update() throws when appeal not found")
    void update_notFound_throws() {
        when(banAppealRepository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("gone", sampleRequest(1L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    /* ─── delete ─── */

    @Test
    @DisplayName("delete() removes appeal and publishes event")
    void delete_success() {
        BanAppeal a = sampleAppeal("a6", 6L);
        when(banAppealRepository.findById("a6")).thenReturn(Optional.of(a));
        doNothing().when(banAppealRepository).deleteById("a6");
        doNothing().when(eventPublisher).publishAppealDeleted("a6", 6L);

        service.delete("a6");

        verify(banAppealRepository).deleteById("a6");
        verify(eventPublisher).publishAppealDeleted("a6", 6L);
    }

    @Test
    @DisplayName("delete() throws when appeal not found")
    void delete_notFound_throws() {
        when(banAppealRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("x"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    /* ─── deleteByUserId ─── */

    @Test
    @DisplayName("deleteByUserId() delegates to repository")
    void deleteByUserId_callsRepo() {
        doNothing().when(banAppealRepository).deleteByUserId(7L);

        service.deleteByUserId(7L);

        verify(banAppealRepository).deleteByUserId(7L);
    }
}
