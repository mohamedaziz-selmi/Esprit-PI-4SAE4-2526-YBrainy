package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.*;
import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import esprit.tn.breadandbutteruser.services.AuthorizationHelper;
import esprit.tn.breadandbutteruser.services.AvatarStorageService;
import esprit.tn.breadandbutteruser.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(roles = "ADMIN")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthorizationHelper authorizationHelper;

    @MockBean
    private AvatarStorageService avatarStorageService;

    // ── GET /api/users/internal/{id} ─────────────────────────────────────────────────

    @Test
    void getUserByIdInternal_success() throws Exception {
        UserDto user = createSampleUserDto(1L, "johndoe", Role.STUDENT);
        when(userService.getUserById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/internal/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("johndoe"));
    }

    @Test
    void getUserByIdInternal_notFound() throws Exception {
        when(userService.getUserById(999L)).thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(get("/api/users/internal/999"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/users/internal/ids ──────────────────────────────────────────────────

    @Test
    void getAllUserIdsInternal_success() throws Exception {
        when(userService.getAllUserIds()).thenReturn(List.of(1L, 2L, 3L));

        mockMvc.perform(get("/api/users/internal/ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0]").value(1));
    }

    // ── GET /api/users/internal/ids-by-role ───────────────────────────────────────────

    @Test
    void getUserIdsByRoleInternal_success() throws Exception {
        when(userService.getUserIdsByRole(Role.STUDENT)).thenReturn(List.of(1L, 2L));

        mockMvc.perform(get("/api/users/internal/ids-by-role")
                        .param("role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getUserIdsByRoleInternal_invalidRole() throws Exception {
        mockMvc.perform(get("/api/users/internal/ids-by-role")
                        .param("role", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/users/internal/first-by-role ──────────────────────────────────────────

    @Test
    void getFirstUserByRoleInternal_found() throws Exception {
        UserDto user = createSampleUserDto(1L, "admin", Role.ADMIN);
        when(userService.getFirstUserByRole(Role.ADMIN)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/internal/first-by-role")
                        .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void getFirstUserByRoleInternal_notFound() throws Exception {
        when(userService.getFirstUserByRole(Role.ADMIN)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/internal/first-by-role")
                        .param("role", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/users/{id} ──────────────────────────────────────────────────────────────

    @Test
    void getUserById_success() throws Exception {
        UserDto user = createSampleUserDto(1L, "johndoe", Role.STUDENT);
        when(userService.getUserById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("johndoe"));
    }

    // ── GET /api/users/username/{username} ─────────────────────────────────────────────

    @Test
    void getUserByUsername_success() throws Exception {
        UserDto user = createSampleUserDto(1L, "johndoe", Role.STUDENT);
        when(userService.getUserByUsername("johndoe")).thenReturn(user);

        mockMvc.perform(get("/api/users/username/johndoe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("johndoe"));
    }

    // ── GET /api/users/email/{email} ────────────────────────────────────────────────────

    @Test
    void getUserByEmail_success() throws Exception {
        UserDto user = createSampleUserDto(1L, "johndoe", Role.STUDENT);
        user.setEmail("john@test.com");
        when(userService.getUserByEmail("john@test.com")).thenReturn(user);

        mockMvc.perform(get("/api/users/email/john@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@test.com"));
    }

    // ── GET /api/users (admin only) ─────────────────────────────────────────────────────

    @Test
    void getAllUsers_success() throws Exception {
        List<UserDto> users = List.of(
                createSampleUserDto(1L, "user1", Role.STUDENT),
                createSampleUserDto(2L, "user2", Role.INSTRUCTOR)
        );
        when(userService.getAllUsers()).thenReturn(users);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── POST /api/users/uploads/avatar ───────────────────────────────────────────────────

    @Test
    void uploadAvatar_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "image content".getBytes());
        var stored = new AvatarStorageService.StoredAvatar("avatar.png", "http://cdn/avatar.png", 1234);
        when(avatarStorageService.storeAvatar(any())).thenReturn(stored);

        mockMvc.perform(multipart("/api/users/uploads/avatar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://cdn/avatar.png"))
                .andExpect(jsonPath("$.fileName").value("avatar.png"))
                .andExpect(jsonPath("$.sizeBytes").value(1234));
    }

    // ── GET /api/users/admin/overview ───────────────────────────────────────────────────

    @Test
    void getAdminUserOverview_withoutPagination() throws Exception {
        AdminUserOverviewDto overview = AdminUserOverviewDto.builder()
                .userId(1L)
                .username("user1")
                .status(IntegrityStatus.SECURE)
                .build();
        when(userService.getAdminUserOverview(null, "all")).thenReturn(List.of(overview));

        mockMvc.perform(get("/api/users/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    void getAdminUserOverview_withPagination() throws Exception {
        AdminUserOverviewDto overview = AdminUserOverviewDto.builder()
                .userId(1L)
                .username("user1")
                .status(IntegrityStatus.SECURE)
                .build();
        UserService.AdminOverviewPageResult page = new UserService.AdminOverviewPageResult(
                List.of(overview), 1L, 1, 0, 20, 0L, 0L, 0L, 0L
        );
        when(userService.getAdminUserOverviewPage("test", "all", 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/users/admin/overview")
                        .param("search", "test")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── GET /api/users/{id}/activities ───────────────────────────────────────────────────

    @Test
    void getUserActivities_success() throws Exception {
        List<UserActivityDto> activities = List.of(
                UserActivityDto.builder()
                        .id(1L)
                        .eventType("login")
                        .route("/api/auth/signin")
                        .durationMs(100L)
                        .occurredAtEpochMs(1710000000000L)
                        .receivedAt(LocalDateTime.now())
                        .role("STUDENT")
                        .build()
        );
        when(userService.getRecentActivities(1L, 20)).thenReturn(activities);

        mockMvc.perform(get("/api/users/1/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventType").value("login"));
    }

    @Test
    void getUserActivities_withLimit() throws Exception {
        List<UserActivityDto> activities = List.of();
        when(userService.getRecentActivities(1L, 50)).thenReturn(activities);

        mockMvc.perform(get("/api/users/1/activities")
                        .param("limit", "50"))
                .andExpect(status().isOk());
    }

    // ── PUT /api/users/{id}/admin-verification ──────────────────────────────────────────────

    @Test
    void setAdminVerification_success() throws Exception {
        UserDto user = createSampleUserDto(1L, "instructor", Role.INSTRUCTOR);
        user.setAdminVerified(true);
        user.setAdminVerifiedBy("admin");
        when(userService.setAdminVerification(eq(1L), eq(true), anyString())).thenReturn(user);
        when(authorizationHelper.actorName(any())).thenReturn("admin");

        mockMvc.perform(put("/api/users/1/admin-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verified\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminVerified").value(true));
    }

    @Test
    void setAdminVerification_nullRequest() throws Exception {
        mockMvc.perform(put("/api/users/1/admin-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/users/{id}/ban ─────────────────────────────────────────────────────────────

    @Test
    void setBan_success() throws Exception {
        UserDto user = createSampleUserDto(1L, "baduser", Role.STUDENT);
        user.setReasonForBan("Violation");
        user.setBanPeriod(7);
        user.setLockedUntil(LocalDateTime.now().plusDays(7));
        user.setStatus(IntegrityStatus.SUSPENDED);
        when(userService.setUserBan(1L, true, "Violation", 7)).thenReturn(user);

        mockMvc.perform(put("/api/users/1/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banned\": true, \"reasonForBan\": \"Violation\", \"banPeriodDays\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonForBan").value("Violation"))
                .andExpect(jsonPath("$.banPeriod").value(7))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.lockedUntil").isNotEmpty());
    }

    @Test
    void setBan_nullRequest() throws Exception {
        mockMvc.perform(put("/api/users/1/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/users/{id} ──────────────────────────────────────────────────────────────

    @Test
    void deleteUser_success() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(1L);
    }

    // ── PUT /api/users/{id} ─────────────────────────────────────────────────────────────────

    @Test
    void updateUser_success() throws Exception {
        UserDto user = createSampleUserDto(1L, "updated", Role.STUDENT);
        user.setFirstName("Updated");
        when(userService.updateUser(eq(1L), org.mockito.ArgumentMatchers.any(UpdateUserDto.class))).thenReturn(user);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"updated\", \"firstName\": \"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));
    }

    // ── POST /api/users/{id}/face-biometric ────────────────────────────────────────────────

    @Test
    void registerFaceBiometric_success() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "face.png", "image/png", "face data".getBytes());
        UserDto user = createSampleUserDto(1L, "user", Role.STUDENT);
        user.setFaceBiometricRegistered(true);
        when(userService.registerFaceBiometric(eq(1L), org.mockito.ArgumentMatchers.any())).thenReturn(user);

        mockMvc.perform(multipart("/api/users/1/face-biometric").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faceBiometricRegistered").value(true));
    }

    // ── DELETE /api/users/{id}/face-biometric ─────────────────────────────────────────────

    @Test
    void removeFaceBiometric_success() throws Exception {
        UserDto user = createSampleUserDto(1L, "user", Role.STUDENT);
        user.setFaceBiometricRegistered(false);
        when(userService.removeFaceBiometric(1L)).thenReturn(user);

        mockMvc.perform(delete("/api/users/1/face-biometric"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faceBiometricRegistered").value(false));
    }

    // ── PUT /api/users/{id}/password ───────────────────────────────────────────────────────

    @Test
    void changePassword_success() throws Exception {
        doNothing().when(userService).changePassword(1L, "NewPass1", "NewPass1");

        mockMvc.perform(put("/api/users/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"NewPass1\", \"confirmPassword\": \"NewPass1\"}"))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(1L, "NewPass1", "NewPass1");
    }

    @Test
    void changePassword_nullRequest() throws Exception {
        mockMvc.perform(put("/api/users/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    private UserDto createSampleUserDto(Long id, String username, Role role) {
        UserDto dto = new UserDto();
        dto.setUserId(id);
        dto.setUsername(username);
        dto.setEmail(username + "@test.com");
        dto.setFirstName("First");
        dto.setLastName("Last");
        dto.setRole(role);
        dto.setAge(25);
        dto.setCountry("Tunisia");
        dto.setFaceBiometricRegistered(false);
        dto.setAdminVerified(false);
        dto.setEnterpriseVerified(false);
        dto.setStatus(IntegrityStatus.SECURE);
        return dto;
    }
}
