package com.example.pmdaily.risk;

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
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.project.ProjectMemberRepository;
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
class RiskIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] ADMIN_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage",
                    "risk:view", "risk:manage", "issue:view", "issue:manage"};
    private static final String[] PM_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage",
                    "risk:view", "risk:manage", "issue:view", "issue:manage"};
    private static final String[] MEMBER_PERMS =
            {"risk:view"};

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
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository memberRepository;
    @Autowired
    private RiskRepository riskRepository;

    private UUID adminId;
    private UUID pmUserId;
    private UUID ownerId;
    private UUID outsiderId;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                statement.executeUpdate("DELETE FROM risks");
                statement.executeUpdate("DELETE FROM issues");
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

        User admin = createUser("admin.rsk", "admin.rsk@example.com", adminRole);
        User pm = createUser("pm.rsk", "pm.rsk@example.com", pmRole);
        User owner = createUser("owner.rsk", "owner.rsk@example.com", memberRole);
        User outsider = createUser("outsider.rsk", "outsider.rsk@example.com", memberRole);

        adminId = admin.getId();
        pmUserId = pm.getId();
        ownerId = owner.getId();
        outsiderId = outsider.getId();

        projectId = createProject(adminToken(), "PRJ-RSK", "Project Risk Test");

        addMember(adminToken(), projectId, pmUserId, ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, ownerId, ProjectMemberRole.DEVELOPER);
    }

    @Test
    void testCreateGetListUpdateDeleteRisk() throws Exception {
        String pmToken = login("pm.rsk", PASSWORD);

        // 1. Create Risk (probability=HIGH, impact=HIGH -> computed level=CRITICAL)
        String createJson = """
                {
                  "projectId": "%s",
                  "title": "Rủi ro chậm tiến độ nâng cấp server",
                  "description": "Nhà cung cấp giao phần cứng trễ",
                  "probability": "HIGH",
                  "impact": "HIGH",
                  "ownerId": "%s",
                  "mitigationPlan": "Thuê server tạm trên Cloud",
                  "contingencyPlan": "Lùi ngày cutover 3 ngày",
                  "dueDate": "%s"
                }
                """.formatted(projectId, ownerId, LocalDate.now().plusDays(10));

        MvcResult createRes = mockMvc.perform(post("/api/v1/risks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.code").value("RSK000001"))
                .andExpect(jsonPath("$.level").value("CRITICAL"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();

        String riskId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        // 2. Get Risk
        mockMvc.perform(get("/api/v1/risks/" + riskId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(riskId))
                .andExpect(jsonPath("$.code").value("RSK000001"));

        // 3. List Risks
        mockMvc.perform(get("/api/v1/risks")
                        .header("Authorization", "Bearer " + pmToken)
                        .param("projectId", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));

        // 4. Update Risk status to MONITORING
        String updateJson = """
                {
                  "title": "Rủi ro chậm tiến độ nâng cấp server (Đang theo dõi)",
                  "description": "Nhà cung cấp đã xác nhận ngày giao",
                  "probability": "MEDIUM",
                  "impact": "HIGH",
                  "ownerId": "%s",
                  "mitigationPlan": "Thuê server tạm",
                  "contingencyPlan": "Lùi cutover 3 ngày",
                  "status": "MONITORING",
                  "dueDate": "%s",
                  "version": 0
                }
                """.formatted(ownerId, LocalDate.now().plusDays(10));

        mockMvc.perform(put("/api/v1/risks/" + riskId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("HIGH"))
                .andExpect(jsonPath("$.status").value("MONITORING"));

        // 5. Delete Risk
        mockMvc.perform(delete("/api/v1/risks/" + riskId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        // Get deleted returns 404
        mockMvc.perform(get("/api/v1/risks/" + riskId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void testConvertToIssue_WhenRiskOccurred_Success() throws Exception {
        String pmToken = login("pm.rsk", PASSWORD);

        // Create Risk
        String createJson = """
                {
                  "projectId": "%s",
                  "title": "Rủi ro mất kết nối gateway thanh toán",
                  "probability": "HIGH",
                  "impact": "HIGH",
                  "ownerId": "%s"
                }
                """.formatted(projectId, ownerId);

        MvcResult createRes = mockMvc.perform(post("/api/v1/risks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        String riskId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        // Trying convert to issue when OPEN -> 400 BAD_REQUEST
        mockMvc.perform(post("/api/v1/risks/" + riskId + "/convert-to-issue")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isBadRequest());

        // Update Risk to OCCURRED
        String updateJson = """
                {
                  "title": "Rủi ro mất kết nối gateway thanh toán",
                  "probability": "HIGH",
                  "impact": "HIGH",
                  "ownerId": "%s",
                  "status": "OCCURRED",
                  "version": 0
                }
                """.formatted(ownerId);

        mockMvc.perform(put("/api/v1/risks/" + riskId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCURRED"));

        // Convert to issue when OCCURRED -> 201 Created
        MvcResult convertRes = mockMvc.perform(post("/api/v1/risks/" + riskId + "/convert-to-issue")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "severity": "CRITICAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.code").value("ISS000001"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andReturn();

        String issueId = objectMapper.readTree(convertRes.getResponse().getContentAsString()).get("id").asText();

        // Check Risk now has linkedIssueId
        mockMvc.perform(get("/api/v1/risks/" + riskId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedIssueId").value(issueId));

        // Trying to convert again -> 409 ALREADY_LINKED
        mockMvc.perform(post("/api/v1/risks/" + riskId + "/convert-to-issue")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isConflict());
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
        return login("admin.rsk", PASSWORD);
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
