package com.esprit.messagingservice.service;

import com.esprit.messagingservice.dto.*;
import com.esprit.messagingservice.feign.UserFeignClient;
import com.esprit.messagingservice.model.PrivateMessage;
import com.esprit.messagingservice.repository.PrivateMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock PrivateMessageRepository messageRepository;
    @Mock UserFeignClient userFeignClient;
    @Mock SimpMessagingTemplate messagingTemplate;

    @InjectMocks MessageService service;

    private UserDto senderDto;
    private UserDto receiverDto;
    private PrivateMessage savedMessage;

    @BeforeEach
    void setUp() {
        senderDto = new UserDto();
        senderDto.setId(10L);
        senderDto.setUsername("alice");
        senderDto.setRole("INSTRUCTOR");
        senderDto.setLevel(3);
        senderDto.setLevelTitle("Expert");

        receiverDto = new UserDto();
        receiverDto.setId(20L);
        receiverDto.setUsername("bob");
        receiverDto.setRole("STUDENT");
        receiverDto.setLevel(1);
        receiverDto.setLevelTitle("Beginner");

        savedMessage = PrivateMessage.builder()
                .id(1L).senderId(10L).receiverId(20L)
                .content("Hello").read(false)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("send persists message and broadcasts to receiver WebSocket topic")
    void send_persistsMessage_andBroadcastsViaWebSocket() {
        when(messageRepository.save(any())).thenReturn(savedMessage);
        when(userFeignClient.getUser(10L)).thenReturn(senderDto);
        when(userFeignClient.getUser(20L)).thenReturn(receiverDto);

        SendMessageRequest req = new SendMessageRequest();
        req.setSenderId(10L);
        req.setReceiverId(20L);
        req.setContent("Hello");

        MessageResponse response = service.send(req);

        assertThat(response.getSenderId()).isEqualTo(10L);
        assertThat(response.getReceiverId()).isEqualTo(20L);
        assertThat(response.getContent()).isEqualTo("Hello");
        assertThat(response.getSenderUsername()).isEqualTo("alice");
        verify(messageRepository).save(any(PrivateMessage.class));
        verify(messagingTemplate).convertAndSend(
                eq("/topic/messages/user-20"), any(MessageResponse.class));
    }

    @Test
    @DisplayName("send falls back to 'User {id}' when Feign throws")
    void send_userFeignFails_usesDefaultUsername() {
        when(messageRepository.save(any())).thenReturn(savedMessage);
        when(userFeignClient.getUser(anyLong())).thenThrow(new RuntimeException("feign error"));

        SendMessageRequest req = new SendMessageRequest();
        req.setSenderId(10L);
        req.setReceiverId(20L);
        req.setContent("Hello");

        MessageResponse response = service.send(req);

        assertThat(response.getSenderUsername()).isEqualTo("User 10");
        assertThat(response.getReceiverUsername()).isEqualTo("User 20");
        assertThat(response.getSenderRole()).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("getConversation delegates to repo and maps to responses")
    void getConversation_returnsListOfResponses() {
        when(messageRepository.findConversation(10L, 20L)).thenReturn(List.of(savedMessage));
        when(userFeignClient.getUser(anyLong())).thenThrow(new RuntimeException());

        List<MessageResponse> result = service.getConversation(10L, 20L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Hello");
        verify(messageRepository).findConversation(10L, 20L);
    }

    @Test
    @DisplayName("getInbox groups by partner and counts unread messages correctly")
    void getInbox_groupsByPartner_andCountsUnread() {
        PrivateMessage older = PrivateMessage.builder().id(1L)
                .senderId(20L).receiverId(10L).content("Hi")
                .read(false).createdAt(LocalDateTime.now().minusMinutes(2)).build();
        PrivateMessage newer = PrivateMessage.builder().id(2L)
                .senderId(20L).receiverId(10L).content("Hey")
                .read(false).createdAt(LocalDateTime.now().minusMinutes(1)).build();

        when(messageRepository.findAllByUserId(10L)).thenReturn(List.of(older, newer));
        when(userFeignClient.getUser(20L)).thenReturn(receiverDto);

        List<ConversationPreviewResponse> inbox = service.getInbox(10L);

        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).getUnreadCount()).isEqualTo(2);
        assertThat(inbox.get(0).getUsername()).isEqualTo("bob");
        assertThat(inbox.get(0).getLastMessage()).isEqualTo("Hey");
    }

    @Test
    @DisplayName("getInbox shows '[media]' for media-only messages with no content")
    void getInbox_mediaOnlyMessage_showsPlaceholder() {
        PrivateMessage media = PrivateMessage.builder().id(3L)
                .senderId(20L).receiverId(10L).content(null)
                .mediaUrl("http://example.com/img.png")
                .read(true).createdAt(LocalDateTime.now()).build();

        when(messageRepository.findAllByUserId(10L)).thenReturn(List.of(media));
        when(userFeignClient.getUser(20L)).thenReturn(receiverDto);

        List<ConversationPreviewResponse> inbox = service.getInbox(10L);

        assertThat(inbox.get(0).getLastMessage()).isEqualTo("[media]");
    }

    @Test
    @DisplayName("getInbox falls back to 'User {id}' when Feign throws for partner lookup")
    void getInbox_feignFails_usesDefaultPartnerName() {
        when(messageRepository.findAllByUserId(10L)).thenReturn(List.of(savedMessage));
        when(userFeignClient.getUser(anyLong())).thenThrow(new RuntimeException());

        List<ConversationPreviewResponse> inbox = service.getInbox(10L);

        assertThat(inbox.get(0).getUsername()).isEqualTo("User 20");
        assertThat(inbox.get(0).getRole()).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("getUnreadCount delegates to repository")
    void getUnreadCount_delegatesToRepo() {
        when(messageRepository.countByReceiverIdAndReadFalse(10L)).thenReturn(5L);

        assertThat(service.getUnreadCount(10L)).isEqualTo(5L);
        verify(messageRepository).countByReceiverIdAndReadFalse(10L);
    }

    @Test
    @DisplayName("markAsRead sets read=true and persists the message")
    void markAsRead_setsReadTrue_andPersists() {
        PrivateMessage unread = PrivateMessage.builder().id(1L)
                .senderId(10L).receiverId(20L).content("Msg")
                .read(false).createdAt(LocalDateTime.now()).build();
        PrivateMessage afterSave = PrivateMessage.builder().id(1L)
                .senderId(10L).receiverId(20L).content("Msg")
                .read(true).createdAt(LocalDateTime.now()).build();

        when(messageRepository.findById(1L)).thenReturn(Optional.of(unread));
        when(messageRepository.save(any())).thenReturn(afterSave);
        when(userFeignClient.getUser(anyLong())).thenThrow(new RuntimeException());

        MessageResponse response = service.markAsRead(1L);

        assertThat(response.isRead()).isTrue();
        verify(messageRepository).save(any());
    }

    @Test
    @DisplayName("markAsRead throws RuntimeException when message not found")
    void markAsRead_notFound_throwsRuntimeException() {
        when(messageRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("markConversationRead delegates to repository")
    void markConversationRead_delegatesToRepo() {
        service.markConversationRead(10L, 20L);

        verify(messageRepository).markConversationAsRead(10L, 20L);
    }

    @Test
    @DisplayName("getAllUsers maps Feign users to UserSummaryResponse")
    void getAllUsers_mapsFeignToSummary() {
        when(userFeignClient.getAllUsers()).thenReturn(List.of(senderDto, receiverDto));

        List<UserSummaryResponse> result = service.getAllUsers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
        assertThat(result.get(1).getUsername()).isEqualTo("bob");
    }

    @Test
    @DisplayName("getAllUsers returns empty list when Feign throws")
    void getAllUsers_feignFails_returnsEmptyList() {
        when(userFeignClient.getAllUsers()).thenThrow(new RuntimeException("service unavailable"));

        List<UserSummaryResponse> result = service.getAllUsers();

        assertThat(result).isEmpty();
    }
}
