package com.example.pmdaily.plan;

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

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.example.pmdaily.PMDailyApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrossPlanRollupIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PROJECT_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};
    private static final String[] PLAN_MANAGE_PERMS =
            {"plan:view", "plan:create", "plan:update", "plan:delete", "plan:approve", "plan:schedule"};

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

    private UUID pmUserId;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                String[] tables = {
                        "plan_change_histories", "plan_change_requests", "plan_links",
                        "plan_baseline_tasks", "plan_baselines", "plan_task_resources",
                        "plan_task_dependencies", "plan_tasks", "plan_versions",
                        "project_plans", "project_members", "projects", "user_roles",
                        "role_permissions", "permissions", "roles", "users"
                };
                for (String t : tables) {
                    try {
                        statement.executeUpdate("DELETE FROM " + t);
                    } catch (java.sql.SQLException ignored) {}
                }
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            return null;
        });

        Role pmRole = createRole("PROJECT_MANAGER", "PM", concat(PROJECT_PERMS, PLAN_MANAGE_PERMS));
        User pm = createUser("pm.rollup", "pm.rollup@example.com", pmRole);
        pmUserId = pm.getId();

        projectId = createProject(pmToken(), "PRJ-ROLLUP", "Project Rollup Test", pmUserId);
    }

    @Test
    void testMilestoneDetailPlanRollup() throws Exception {
        String pmToken = pmToken();

        // 1. Create Master Plan
        MvcResult masterPlanRes = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "M-01", "Master Plan", "MASTER", null, null)))
                .andExpect(status().isCreated())
                .andReturn();
        String masterPlanId = json(masterPlanRes, "id");

        // 2. Create a Milestone Task in Master Plan
        String milestoneTaskJson = """
                {
                    "taskCode": "M-TASK-1",
                    "taskName": "Milestone Bàn giao",
                    "taskType": "MILESTONE",
                    "plannedStart": "2026-09-01",
                    "plannedFinish": "2026-09-01",
                    "durationMinutes": 0,
                    "plannedEffortMinutes": 0
                }
                """;
        MvcResult milestoneRes = mockMvc.perform(post("/api/v1/plans/" + masterPlanId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(milestoneTaskJson))
                .andExpect(status().isCreated())
                .andReturn();
        String milestoneTaskId = json(milestoneRes, "id");

        // 3. Create Detail Plan linked to that Milestone
        MvcResult detailPlanRes = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "D-01", "Detail Plan 1", "DETAIL", masterPlanId, milestoneTaskId)))
                .andExpect(status().isCreated())
                .andReturn();
        String detailPlanId = json(detailPlanRes, "id");

        // 4. Create Task 1 under Detail Plan (Duration = 3 days (1440 mins), Progress = 100)
        String task1Json = """
                {
                    "taskCode": "DET-1",
                    "taskName": "Coding backend",
                    "taskType": "TASK",
                    "plannedStart": "2026-09-01",
                    "plannedFinish": "2026-09-03",
                    "durationMinutes": 1440,
                    "durationUnit": "DAY",
                    "plannedEffortMinutes": 1440,
                    "effortUnit": "DAY",
                    "percentComplete": 100
                }
                """;
        mockMvc.perform(post("/api/v1/plans/" + detailPlanId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(task1Json))
                .andExpect(status().isCreated());

        // 5. Create Task 2 under Detail Plan (Duration = 2 days (960 mins), Progress = 50)
        String task2Json = """
                {
                    "taskCode": "DET-2",
                    "taskName": "QA Testing",
                    "taskType": "TASK",
                    "plannedStart": "2026-09-04",
                    "plannedFinish": "2026-09-05",
                    "durationMinutes": 960,
                    "durationUnit": "DAY",
                    "plannedEffortMinutes": 960,
                    "effortUnit": "DAY",
                    "percentComplete": 50
                }
                """;
        MvcResult task2Res = mockMvc.perform(post("/api/v1/plans/" + detailPlanId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(task2Json))
                .andExpect(status().isCreated())
                .andReturn();
        String task2Id = json(task2Res, "id");
        long task2Version = objectMapper.readTree(task2Res.getResponse().getContentAsString()).get("version").asLong();

        // 6. Verify that the parent milestone's progress has rolled up automatically:
        // totalDuration = 1440 + 960 = 2400 mins
        // weightedProgress = (1440 * 100) + (960 * 50) = 144000 + 48000 = 192000
        // expectedProgress = 192000 / 2400 = 80%
        // expectedFinish = 2026-09-05
        MvcResult getRes = mockMvc.perform(get("/api/v1/plans/" + masterPlanId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listNode = objectMapper.readTree(getRes.getResponse().getContentAsString());
        JsonNode milestoneNode = null;
        for (JsonNode node : listNode) {
            if (node.get("id").asText().equals(milestoneTaskId)) {
                milestoneNode = node;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(milestoneNode);
        org.junit.jupiter.api.Assertions.assertEquals(80, milestoneNode.get("percentComplete").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("2026-09-05", milestoneNode.get("plannedFinish").asText());

        // 7. Update Task 2 to 100% complete
        String task2UpdateJson = """
                {
                    "taskName": "QA Testing (Done)",
                    "taskType": "TASK",
                    "plannedStart": "2026-09-04",
                    "plannedFinish": "2026-09-05",
                    "durationMinutes": 960,
                    "durationUnit": "DAY",
                    "plannedEffortMinutes": 960,
                    "effortUnit": "DAY",
                    "percentComplete": 100,
                    "status": "COMPLETED",
                    "version": %d
                }
                """.formatted(task2Version);

        mockMvc.perform(put("/api/v1/plans/" + detailPlanId + "/tasks/" + task2Id)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(task2UpdateJson))
                .andExpect(status().isOk());

        // 8. Milestone progress should now be 100% and status should be COMPLETED
        MvcResult getRes2 = mockMvc.perform(get("/api/v1/plans/" + masterPlanId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listNode2 = objectMapper.readTree(getRes2.getResponse().getContentAsString());
        JsonNode milestoneNode2 = null;
        for (JsonNode node : listNode2) {
            if (node.get("id").asText().equals(milestoneTaskId)) {
                milestoneNode2 = node;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(milestoneNode2);
        org.junit.jupiter.api.Assertions.assertEquals(100, milestoneNode2.get("percentComplete").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", milestoneNode2.get("status").asText());
    }

    // ------------------------------------------------------------------ HELPERS
    private String pmToken() throws Exception {
        return login("pm.rollup", PASSWORD);
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

    private String createProject(String token, String code, String name, UUID pmId) throws Exception {
        String json = """
                {
                  "code": "%s",
                  "name": "%s",
                  "startDate": "2026-08-01",
                  "endDate": "2026-12-31",
                  "projectManagerId": "%s"
                }
                """.formatted(code, name, pmId);
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

    private String createPlanJson(String prjId, String code, String name, String type, String parentId, String milestoneId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"projectId\":\"").append(prjId)
                .append("\",\"planCode\":\"").append(code)
                .append("\",\"planName\":\"").append(name)
                .append("\",\"planType\":\"").append(type);
        if (parentId != null) {
            sb.append("\",\"parentPlanId\":\"").append(parentId);
        }
        if (milestoneId != null) {
            sb.append("\",\"parentMilestoneTaskId\":\"").append(milestoneId);
        }
        sb.append("\",\"plannedStart\":\"2026-09-01\",\"plannedFinish\":\"2026-12-31\"}");
        return sb.toString();
    }

    private String json(MvcResult res, String field) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).get(field).asText();
    }

    private String[] concat(String[] a, String[] b) {
        String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
