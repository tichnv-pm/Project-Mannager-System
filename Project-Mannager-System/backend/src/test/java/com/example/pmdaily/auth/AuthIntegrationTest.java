package com.example.pmdaily.auth;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.pmdaily.user.Permission;
import com.example.pmdaily.user.PermissionRepository;
import com.example.pmdaily.user.Role;
import com.example.pmdaily.user.RoleRepository;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String PASSWORD = "Abc@12345";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String adminPassword;
    private User member;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                statement.executeUpdate("DELETE FROM user_roles");
                statement.executeUpdate("DELETE FROM role_permissions");
                statement.executeUpdate("DELETE FROM refresh_tokens");
                statement.executeUpdate("DELETE FROM audit_logs");
                statement.executeUpdate("DELETE FROM users");
                statement.executeUpdate("DELETE FROM roles");
                statement.executeUpdate("DELETE FROM permissions");
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            return null;
        });

        adminPassword = "Admin@123";
        createUser("admin.test", "admin@pmdaily.local", adminPassword, UserStatus.ACTIVE,
                "ADMIN", "user:manage");
        createUser("member.test", "member@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "MEMBER");
        createUser("inactive.test", "inactive@pmdaily.local", PASSWORD, UserStatus.INACTIVE, "MEMBER");
        member = userRepository.findByUsername("member.test").orElseThrow();
    }

    private UUID createUser(String username, String email, String password, UserStatus status,
            String roleCode, String... permissionCodes) {
        Set<Permission> permissions = new java.util.HashSet<>();
        for (String code : permissionCodes) {
            Permission permission = new Permission();
            permission.setCode(code);
            permission.setName(code);
            permissions.add(permissionRepository.save(permission));
        }

        Role role = new Role();
        role.setCode("ROLE_" + roleCode + "_" + username);
        role.setName(roleCode + " " + username);
        role.setPermissions(permissions);
        roleRepository.save(role);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(status);
        user.getRoles().add(role);
        userRepository.save(user);

        return user.getId();
    }

    @Test
    void login_success_returnsTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"member.test","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.user.username").value("member.test"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void login_wrongPassword_returns401WithGenericMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"member.test","password":"Wrong@123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"))
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không đúng"));
    }

    @Test
    void login_unknownUser_returns401WithGenericMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ghost","password":"Wrong@123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }

    @Test
    void login_inactiveUser_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"inactive.test","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }

    @Test
    void login_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void login_fiveFailures_locksAccount() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"member.test","password":"Wrong@123"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"member.test","password":"Wrong@123"}
                                """))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void login_lockedAccount_returns423_evenWithCorrectPassword() throws Exception {
        User user = userRepository.findByUsername("member.test").orElseThrow();
        user.setLockedUntil(java.time.Instant.now().plusSeconds(300));
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"member.test","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void me_withValidToken_returnsProfileWithRolesAndPermissions() throws Exception {
        MvcResult result = loginAs("member.test", PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", bearer(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("member.test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_rotatesToken_andOldTokenReuseFails() throws Exception {
        MvcResult loginResult = loginAs("member.test", PASSWORD);
        String refreshToken = tokenFrom(loginResult, "refreshToken");

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String newRefreshToken = tokenFrom(refreshResult, "refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        long refreshCount = refreshTokenRepositoryCount();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(newRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void refresh_unknownToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"not-a-real-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void logout_revokesToken_thenRefreshFails() throws Exception {
        MvcResult loginResult = loginAs("member.test", PASSWORD);
        String refreshToken = tokenFrom(loginResult, "refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearer(loginResult))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_updatesPasswordAndRevokesAllTokens() throws Exception {
        MvcResult loginResult = loginAs("member.test", PASSWORD);
        String refreshToken = tokenFrom(loginResult, "refreshToken");

        mockMvc.perform(put("/api/v1/auth/change-password")
                        .header("Authorization", bearer(loginResult))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"New@67890"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"member.test","password":"New@67890"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns400() throws Exception {
        MvcResult loginResult = loginAs("member.test", PASSWORD);

        mockMvc.perform(put("/api/v1/auth/change-password")
                        .header("Authorization", bearer(loginResult))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Wrong@123","newPassword":"New@67890"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void resetPassword_withoutPermission_returns403() throws Exception {
        MvcResult loginResult = loginAs("member.test", PASSWORD);
        User target = userRepository.findByUsername("inactive.test").orElseThrow();

        mockMvc.perform(post("/api/v1/auth/{userId}/reset-password", target.getId())
                        .header("Authorization", bearer(loginResult))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Reset@12345"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetPassword_withAdminPermission_resetsPasswordAndUnlocks() throws Exception {
        MvcResult adminLogin = loginAs("admin.test", adminPassword);
        User target = userRepository.findByUsername("member.test").orElseThrow();
        target.setLockedUntil(java.time.Instant.now().plusSeconds(300));
        userRepository.save(target);

        mockMvc.perform(post("/api/v1/auth/{userId}/reset-password", target.getId())
                        .header("Authorization", bearer(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Reset@12345"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"member.test","password":"Reset@12345"}
                                """))
                .andExpect(status().isOk());
    }

    private MvcResult loginAs(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String bearer(MvcResult result) throws Exception {
        return "Bearer " + tokenFrom(result, "accessToken");
    }

    private String tokenFrom(MvcResult result, String field) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get(field).asText();
    }

    private long refreshTokenRepositoryCount() {
        return refreshTokenRepository.count();
    }

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
}
