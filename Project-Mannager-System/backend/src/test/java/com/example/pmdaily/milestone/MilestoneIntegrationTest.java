package com.example.pmdaily.milestone;

import java.time.LocalDate;
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

import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.user.Permission;
import com.example.pmdaily.user.PermissionRepository;
import com.example.pmdaily.user.Role;
import com.example.pmdaily.user.RoleRepository;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MilestoneIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] ADMIN_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage",
                    "milestone:view", "milestone:manage"};
    private static final String[] PM_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage",
                    "milestone:view", "milestone:manage"};
    private static final String[] MEMBER_PERMS =
            {"milestone:view"};

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
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                statement.executeUpdate("DELETE FROM milestones");
                statement.executeUpdate("DELETE FROM project_members");
                statement.executeUpdate("DELETE FROM projects");
                statement.executeUpdate("DELETE FROM user_roles");
                statement.executeUpdate("DELETE FROM role_permissions");
                statement.executeUpdate("DELETE FROM permissions");
                statement.executeUpdate("DELETE FROM roles");
                statement.executeUpdate("DELETE FROM users");
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            return null;
        });

        Role adminRole = createRole("ADMIN", "Admin", ADMIN_PERMS);
        Role pmRole = createRole("PROJECT_MANAGER", "PM", PM_PERMS);
        Role memberRole = createRole("PROJECT_MEMBER", "Member", MEMBER_PERMS);

        User admin = createUser("admin.mls", "admin.mls@example.com", adminRole);
        User pm = createUser("pm.mls", "pm.mls@example.com", pmRole);

        adminId = admin.getId();
        pmUserId = pm.getId();

        projectId = createProject(adminToken(), "PRJ-MLS", "Project Milestone Test");
        addMember(adminToken(), projectId, pmUserId, ProjectMemberRole.PROJECT_MANAGER);
    }

    @Test
    void testMilestoneLifecycle_CreateGetListUpdateDelete() throws Exception {
        String pmToken = login("pm.mls", PASSWORD);

        // 1. Create Milestone
        String createJson = """
                {
                  "projectId": "%s",
                  "name": "Hoàn thành UAT Release 1.0",
                  "description": "Nghiệm thu toàn bộ tính năng phase 1",
                  "plannedDate": "%s",
                  "note": "Cần chốt biên bản nghiệm thu trước release"
                }
                """.formatted(projectId, LocalDate.now().plusDays(30));

        MvcResult createRes = mockMvc.perform(post("/api/v1/milestones")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.name").value("Hoàn thành UAT Release 1.0"))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.progress").value(0))
                .andExpect(jsonPath("$.projectCode").value("PRJ-MLS"))
                .andExpect(jsonPath("$.projectName").value("Project Milestone Test"))
                .andReturn();

        String milestoneId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        // 2. Get Milestone
        mockMvc.perform(get("/api/v1/milestones/" + milestoneId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(milestoneId))
                .andExpect(jsonPath("$.name").value("Hoàn thành UAT Release 1.0"));

        // 3. List Milestones
        mockMvc.perform(get("/api/v1/milestones")
                        .header("Authorization", "Bearer " + pmToken)
                        .param("projectId", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));

        // 4. Update Milestone status to COMPLETED with progress < 100 -> 400 Bad Request
        String invalidUpdateJson = """
                {
                  "name": "Hoàn thành UAT Release 1.0",
                  "plannedDate": "%s",
                  "status": "COMPLETED",
                  "progress": 80,
                  "version": 0
                }
                """.formatted(LocalDate.now().plusDays(30));

        mockMvc.perform(put("/api/v1/milestones/" + milestoneId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUpdateJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROGRESS_REQUIRED_FOR_DONE"));

        // 5. Valid Update to COMPLETED with progress = 100 -> actualDate auto populated
        String validUpdateJson = """
                {
                  "name": "Hoàn thành UAT Release 1.0",
                  "plannedDate": "%s",
                  "status": "COMPLETED",
                  "progress": 100,
                  "version": 0
                }
                """.formatted(LocalDate.now().plusDays(30));

        mockMvc.perform(put("/api/v1/milestones/" + milestoneId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.actualDate").value(notNullValue()));

        // 6. Delete Milestone
        mockMvc.perform(delete("/api/v1/milestones/" + milestoneId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        // Get deleted returns 404
        mockMvc.perform(get("/api/v1/milestones/" + milestoneId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNotFound());
    }

    private Role createRole(String code, String name, String[] perms) {
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role = roleRepository.save(role);
        for (String pCode : perms) {
            Permission p = permissionRepository.findByCode(pCode)
                    .orElseGet(() -> {
                        Permission perm = new Permission();
                        perm.setCode(pCode);
                        perm.setName(pCode);
                        return permissionRepository.save(perm);
                    });
            role.getPermissions().add(p);
        }
        return roleRepository.save(role);
    }

    private User createUser(String username, String email, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(username);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    private String login(String username, String password) throws Exception {
        String json = objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        return node.get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        return login("admin.mls", PASSWORD);
    }

    private String createProject(String token, String code, String name) throws Exception {
        String json = """
                {
                  "code": "%s",
                  "name": "%s",
                  "startDate": "2026-08-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(code, name);
        MvcResult res = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void addMember(String token, String prjId, UUID userId, ProjectMemberRole role) throws Exception {
        String json = """
                {
                  "userId": "%s",
                  "role": "%s"
                }
                """.formatted(userId, role);
        mockMvc.perform(post("/api/v1/projects/" + prjId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }
}
