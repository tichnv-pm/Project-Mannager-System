package com.example.pmdaily.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test User Admin module (docs/api/02-user-admin-api.md, FR-USER-01/02).
 */
@SpringBootTest(classes = com.example.pmdaily.PMDailyApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAdminIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private UUID adminId;
    private UUID pmUserId;
    private UUID memberRoleId;
    private UUID adminRoleId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                statement.executeUpdate("DELETE FROM refresh_tokens");
                statement.executeUpdate("DELETE FROM user_roles");
                statement.executeUpdate("DELETE FROM role_permissions");
                statement.executeUpdate("DELETE FROM audit_logs");
                statement.executeUpdate("DELETE FROM users");
                statement.executeUpdate("DELETE FROM roles");
                statement.executeUpdate("DELETE FROM permissions");
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            return null;
        });

        adminRoleId = createRole("ADMIN", "user:view", "user:manage", "role:manage", "audit:view");
        memberRoleId = createRole("PROJECT_MEMBER", "task:view", "task:create");
        Role pmRole = createRoleEntity("PROJECT_MANAGER");
        adminId = createUser("admin.ua", "admin.ua@pmdaily.local", UserStatus.ACTIVE, adminRoleId);
        pmUserId = createUser("pm.ua", "pm.ua@pmdaily.local", UserStatus.ACTIVE, pmRole.getId());
    }

    private UUID createRole(String code, String... permissionCodes) {
        Role role = createRoleEntity(code, permissionCodes);
        return role.getId();
    }

    private Role createRoleEntity(String code, String... permissionCodes) {
        Set<Permission> permissions = new HashSet<>();
        for (String permCode : permissionCodes) {
            Permission permission = permissionRepository.findByCode(permCode).orElseGet(() -> {
                Permission created = new Permission();
                created.setCode(permCode);
                created.setName(permCode);
                return permissionRepository.save(created);
            });
            permissions.add(permission);
        }
        Role role = new Role();
        role.setCode(code);
        role.setName(code);
        role.setPermissions(permissions);
        // V3__admin_module_enhancements.sql: hệ thống đánh dấu các vai trò mặc định là is_system.
        role.setSystem(List.of("ADMIN", "PROJECT_MANAGER", "PROJECT_MEMBER", "VIEWER").contains(code));
        return roleRepository.save(role);
    }

    private UUID createUser(String username, String email, UserStatus status, UUID roleId) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(status);
        user.getRoles().add(roleRepository.findById(roleId).orElseThrow());
        userRepository.save(user);
        return user.getId();
    }

    private String login(String username) throws Exception {
        String body = """
                { "username": "%s", "password": "%s" }
                """.formatted(username, PASSWORD);
        String content = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(content);
        return node.get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        return login("admin.ua");
    }

    @Test
    void listUsers_shouldFilterAndPaginate() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("page", "0").param("size", "10")
                        .param("keyword", "pm.ua"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("pm.ua"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].roles[0]").value("PROJECT_MANAGER"))
                .andExpect(jsonPath("$.content[0].permissions").isArray());
    }

    @Test
    void listUsers_shouldFilterByRoleCode() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("roleCode", "PROJECT_MEMBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listUsers_shouldValidateSortWhitelist() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getUsers_shouldRequireUserViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(adminId.toString()));

        // PM không có user:view → 403
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + login("pm.ua")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_shouldSucceedWithDefaultRole() throws Exception {
        String body = """
                { "username": "member.new", "email": "member.new@pmdaily.local",
                  "fullName": "Thành viên mới", "password": "%s" }
                """.formatted(PASSWORD);
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("member.new"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("PROJECT_MEMBER"));
    }

    @Test
    void createUser_shouldRejectDuplicateUsernameAndEmail() throws Exception {
        String body = """
                { "username": "pm.ua", "email": "other@pmdaily.local",
                  "fullName": "Trùng username", "password": "%s" }
                """.formatted(PASSWORD);
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        String body2 = """
                { "username": "other.ua", "email": "pm.ua@pmdaily.local",
                  "fullName": "Trùng email", "password": "%s" }
                """.formatted(PASSWORD);
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void createUser_shouldValidatePasswordAndFields() throws Exception {
        String weakPassword = """
                { "username": "weak.ua", "email": "weak@pmdaily.local",
                  "fullName": "Yếu", "password": "abc123" }
                """;
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weakPassword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String badEmail = """
                { "username": "bademail.ua", "email": "khong-phai-email",
                  "fullName": "Sai email", "password": "%s" }
                """.formatted(PASSWORD);
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badEmail))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_shouldChangeInfoAndRoles() throws Exception {
        String body = """
                { "fullName": "PM đã sửa", "email": "pm.updated@pmdaily.local",
                  "roleIds": ["%s"], "version": 0 }
                """.formatted(memberRoleId);
        mockMvc.perform(put("/api/v1/users/" + pmUserId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("PM đã sửa"))
                .andExpect(jsonPath("$.roles[0]").value("PROJECT_MEMBER"));
    }

    @Test
    void updateUser_shouldRejectStaleVersion() throws Exception {
        String body = """
                { "fullName": "Version cũ", "email": "pm2@pmdaily.local", "version": 99 }
                """;
        mockMvc.perform(put("/api/v1/users/" + pmUserId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void changeStatus_shouldDeactivateAndRevokeTokens() throws Exception {
        UUID target = createUser("ghost.ua", "ghost.ua@pmdaily.local", UserStatus.ACTIVE, memberRoleId);
        String ghostToken = login("ghost.ua");
        long ghostVersion = userRepository.findById(target).orElseThrow().getVersion();

        String body = """
                { "status": "INACTIVE", "version": %d }
                """.formatted(ghostVersion);
        mockMvc.perform(patch("/api/v1/users/" + target + "/status")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        // Ghost không còn login được
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "ghost.ua", "password": "%s" }
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeStatus_shouldNotAllowSelfDeactivation() throws Exception {
        String adminToken = adminToken();
        long adminVersion = userRepository.findById(adminId).orElseThrow().getVersion();
        String body = """
                { "status": "INACTIVE", "version": %d }
                """.formatted(adminVersion);
        mockMvc.perform(patch("/api/v1/users/" + adminId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listRoles_shouldReturnWithPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").value("ADMIN"))
                .andExpect(jsonPath("$[0].permissions").isArray());
    }

    @Test
    void updateRole_shouldReplaceAllPermissions() throws Exception {
        String body = """
                { "name": "PROJECT_MEMBER", "permissionCodes": ["task:view"] }
                """;
        mockMvc.perform(put("/api/v1/roles/" + memberRoleId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(1))
                .andExpect(jsonPath("$.permissions[0]").value("task:view"));
    }

    @Test
    void updateRole_shouldRejectBlankName() throws Exception {
        String body = """
                { "name": "  " }
                """;
        mockMvc.perform(put("/api/v1/roles/" + memberRoleId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateRole_shouldProtectAdminRoleManage() throws Exception {
        String body = """
                { "name": "ADMIN", "permissionCodes": ["task:view"] }
                """;
        mockMvc.perform(put("/api/v1/roles/" + adminRoleId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_shouldRequireUserManagePermission() throws Exception {
        String body = """
                { "username": "pm-create.ua", "email": "pm-create@pmdaily.local",
                  "fullName": "PM tạo user", "password": "%s" }
                """.formatted(PASSWORD);
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + login("pm.ua"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRole_shouldSucceedWithCustomPermissions() throws Exception {
        String body = """
                { "code": "QA_LEAD", "name": "QA Lead",
                  "description": "Lead QA", "permissionCodes": ["task:view", "task:create"] }
                """;
        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("QA_LEAD"))
                .andExpect(jsonPath("$.isSystem").value(false))
                .andExpect(jsonPath("$.permissions.length()").value(2));
    }

    @Test
    void createRole_shouldRejectDuplicateCode() throws Exception {
        String body = """
                { "code": "ADMIN", "name": "Trùng mã" }
                """;
        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void deleteRole_shouldRejectSystemRole() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/" + adminRoleId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRole_shouldDeleteCustomRole() throws Exception {
        Role custom = createRoleEntity("QA_LEAD");
        mockMvc.perform(delete("/api/v1/roles/" + custom.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'QA_LEAD')]").isEmpty());
    }

    @Test
    void deleteUser_shouldSoftDeleteAndRevokeTokens() throws Exception {
        UUID target = createUser("ghost-del.ua", "ghost-del@pmdaily.local", UserStatus.ACTIVE, memberRoleId);
        String ghostToken = login("ghost-del.ua");

        mockMvc.perform(delete("/api/v1/users/" + target)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        // Không còn login được sau khi xóa mềm
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "ghost-del.ua", "password": "%s" }
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());

        // Không xuất hiện trong danh sách users
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("keyword", "ghost-del"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // GET theo id → 404
        mockMvc.perform(get("/api/v1/users/" + target)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_shouldNotAllowSelfDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }
}
