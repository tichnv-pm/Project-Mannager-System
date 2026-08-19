package com.example.pmdaily.project;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test Project module (docs/api/04-project-api.md, BR-PROJ).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] ADMIN_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};
    private static final String[] PM_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};

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
    private UUID viewerId;
    private UUID devUserId;
    private UUID inactiveId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                statement.executeUpdate("DELETE FROM tasks");
                statement.executeUpdate("DELETE FROM project_members");
                statement.executeUpdate("DELETE FROM projects");
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

        adminId = createUser("admin.pj", "admin.pj@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "ADMIN", ADMIN_PERMS);
        pmUserId = createUser("pm.pj", "pm.pj@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "PROJECT_MANAGER", PM_PERMS);
        viewerId = createUser("viewer.pj", "viewer.pj@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "MEMBER",
                "project:view");
        devUserId = createUser("dev.pj", "dev.pj@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "MEMBER");
        inactiveId = createUser("ghost.pj", "ghost.pj@pmdaily.local", PASSWORD, UserStatus.INACTIVE, "MEMBER");
    }

    private UUID createUser(String username, String email, String password, UserStatus status,
            String roleCode, String... permissionCodes) {
        Set<Permission> permissions = new java.util.HashSet<>();
        for (String code : permissionCodes) {
            Permission permission = permissionRepository.findByCode(code).orElseGet(() -> {
                Permission created = new Permission();
                created.setCode(code);
                created.setName(code);
                return permissionRepository.save(created);
            });
            permissions.add(permission);
        }

        Role role = new Role();
        role.setCode("MEMBER".equals(roleCode) ? "MEMBER_" + username : roleCode);
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

    private String createProject(String code, UUID managerId) throws Exception {
        String body = managerId != null
                ? """
                        {"code":"%s","name":"Dự án %s","description":"desc","status":"ACTIVE",
                         "startDate":"2026-05-01","endDate":"2026-11-30","projectManagerId":"%s"}
                        """.formatted(code, code, managerId)
                : """
                        {"code":"%s","name":"Dự án %s","description":"desc","status":"PLANNING"}
                        """.formatted(code, code);
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private MvcResult admin() throws Exception {
        return loginAs("admin.pj", PASSWORD);
    }

    private MvcResult pm() throws Exception {
        return loginAs("pm.pj", PASSWORD);
    }

    private MvcResult viewer() throws Exception {
        return loginAs("viewer.pj", PASSWORD);
    }

    private MvcResult dev() throws Exception {
        return loginAs("dev.pj", PASSWORD);
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

    // ------------------------------------------------------------------ CRUD

    @Test
    void create_success_returns201_withManagerAsMember() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PRJ001","name":"App Mobile Banking","status":"ACTIVE",
                                 "startDate":"2026-05-01","endDate":"2026-11-30",
                                 "projectManagerId":"%s"}
                                """.formatted(pmUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PRJ001"))
                .andExpect(jsonPath("$.projectManagerId").value(pmUserId.toString()))
                .andExpect(jsonPath("$.memberCount").value(1));

        String projectId = createProjectId("PRJ001");
        mockMvc.perform(get("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(pmUserId.toString()))
                .andExpect(jsonPath("$[0].role").value("PROJECT_MANAGER"));
    }

    @Test
    void create_duplicateCode_returns409() throws Exception {
        createProject("PRJ001", pmUserId);

        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PRJ001","name":"Trùng mã","status":"PLANNING"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void create_invalidDateRange_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PRJ002","name":"Sai ngày","startDate":"2026-12-01",
                                 "endDate":"2026-05-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void create_inactiveManager_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PRJ003","name":"PM chết","projectManagerId":"%s"}
                                """.formatted(inactiveId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_withoutPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(dev()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PRJ004","name":"Không quyền","status":"PLANNING"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"AB","name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void get_success_returnsProjectWithMemberCount() throws Exception {
        String projectId = createProject("PRJ005", pmUserId);

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRJ005"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.projectManagerId").value(pmUserId.toString()));
    }

    @Test
    void get_notMember_returns403() throws Exception {
        String projectId = createProject("PRJ006", pmUserId);

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(viewer())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void get_member_canView() throws Exception {
        String projectId = createProject("PRJ007", pmUserId);

        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(viewerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(viewer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRJ007"));
    }

    @Test
    void list_filtersByKeywordAndStatus() throws Exception {
        createProject("PRJ010", pmUserId);
        createProject("PRJ011", null);

        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .param("keyword", "PRJ01")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("PRJ010"));
    }

    @Test
    void list_invalidSort_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .param("sort", "hacked"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_invalidPageSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_myOnly_returnsOnlyJoinedProjects() throws Exception {
        createProject("PRJ020", pmUserId);
        String projectId = createProject("PRJ021", pmUserId);
        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(viewerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(viewer()))
                        .param("myOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("PRJ021"));
    }

    @Test
    void list_memberWithoutAdminRole_seesOnlyJoinedProjects() throws Exception {
        createProject("PRJ030", pmUserId);
        String projectId = createProject("PRJ031", pmUserId);
        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(viewerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(viewer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void update_byProjectManager_success() throws Exception {
        String projectId = createProject("PRJ040", pmUserId);

        mockMvc.perform(put("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PRJ040","name":"Đổi tên","status":"ON_HOLD","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Đổi tên"))
                .andExpect(jsonPath("$.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void update_staleVersion_returns409() throws Exception {
        String projectId = createProject("PRJ041", pmUserId);

        mockMvc.perform(put("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PRJ041","name":"Cũ","version":99}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void update_changeManager_whenNewManagerAlreadyMember_promotesToProjectManager() throws Exception {
        String projectId = createProject("PRJ043", pmUserId);
        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(viewerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PRJ043","name":"Đổi PM","version":0,"projectManagerId":"%s"}
                                """.formatted(viewerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectManagerId").value(viewerId.toString()));

        mockMvc.perform(get("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("PROJECT_MANAGER"))
                .andExpect(jsonPath("$[1].userId").value(viewerId.toString()))
                .andExpect(jsonPath("$[1].role").value("PROJECT_MANAGER"));
    }

    @Test
    void update_byNonManagerMember_returns403() throws Exception {
        String projectId = createProject("PRJ042", pmUserId);
        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(viewerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(viewer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PRJ042","name":"X","version":0}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void delete_activeWithOpenTask_withoutConfirm_returns400() throws Exception {
        String projectId = createProject("PRJ050", pmUserId);
        jdbcTemplate.update("""
                INSERT INTO tasks (id, code, project_id, reporter_id, title, created_at, updated_at, blocked, progress, version, priority, status, type)
                VALUES (?, ?, ?, ?, ?, now(), now(), false, 0, 0, 'MEDIUM', 'TODO', 'TASK')
                """, UUID.randomUUID(), "TSK-001", UUID.fromString(projectId), adminId, "Task mở");

        mockMvc.perform(delete("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void delete_activeWithOpenTask_withConfirm_returns204() throws Exception {
        String projectId = createProject("PRJ051", pmUserId);
        jdbcTemplate.update("""
                INSERT INTO tasks (id, code, project_id, reporter_id, title, created_at, updated_at, blocked, progress, version, priority, status, type)
                VALUES (?, ?, ?, ?, ?, now(), now(), false, 0, 0, 'MEDIUM', 'TODO', 'TASK')
                """, UUID.randomUUID(), "TSK-002", UUID.fromString(projectId), adminId, "Task mở");

        mockMvc.perform(delete("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(admin()))
                        .param("confirm", "true"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_success_thenGetReturns404() throws Exception {
        String projectId = createProject("PRJ052", pmUserId);

        mockMvc.perform(delete("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .param("keyword", "PRJ052"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------------ members

    @Test
    void addMember_success_returns201() throws Exception {
        String projectId = createProject("PRJ060", pmUserId);

        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(viewerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(viewerId.toString()))
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.username").value("viewer.pj"));
    }

    @Test
    void addMember_duplicate_returns409() throws Exception {
        String projectId = createProject("PRJ061", pmUserId);
        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(viewerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"TESTER"}
                                """.formatted(viewerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void addMember_byNonManager_returns403() throws Exception {
        String projectId = createProject("PRJ062", pmUserId);

        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(viewer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(devUserId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void changeRole_success() throws Exception {
        String projectId = createProject("PRJ070", pmUserId);
        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(viewerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/projects/{id}/members/{userId}", projectId, viewerId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"TECH_LEAD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TECH_LEAD"));
    }

    @Test
    void changeRole_lastProjectManager_returns400() throws Exception {
        String projectId = createProject("PRJ071", pmUserId);

        mockMvc.perform(put("/api/v1/projects/{id}/members/{userId}", projectId, pmUserId)
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"DEVELOPER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_MANAGER_REQUIRED"));
    }

    @Test
    void removeMember_success_returns204() throws Exception {
        String projectId = createProject("PRJ072", pmUserId);
        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(viewerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/projects/{id}/members/{userId}", projectId, viewerId)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void removeLastProjectManager_returns400() throws Exception {
        String projectId = createProject("PRJ073", pmUserId);

        mockMvc.perform(delete("/api/v1/projects/{id}/members/{userId}", projectId, pmUserId)
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_MANAGER_REQUIRED"));
    }

    private String createProjectId(String code) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .param("keyword", code))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("content").get(0).get("id").asText();
    }
}
