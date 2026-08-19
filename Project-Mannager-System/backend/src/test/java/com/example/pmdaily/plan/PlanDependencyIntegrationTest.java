package com.example.pmdaily.plan;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests PLN-BE-03 — plan-dependency (docs/api/13-planning-api.md muc 2.3).
 * Cover PLN-AC-DEP-01..06: tạo dep cùng plan, chặn self/cycle/cross-plan, FS/SS/FF/SF,
 * lag âm được phép, xóa task dọn dependency (cascade), phân quyền plan:update.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanDependencyIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PROJECT_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};
    private static final String[] PLAN_MANAGE_PERMS =
            {"plan:view", "plan:create", "plan:update", "plan:delete", "plan:approve"};
    private static final String[] MEMBER_PERMS = {"plan:view"};

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
    private UUID memberUserId;
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
                        "project_plans", "plan_calendar_exceptions", "plan_calendar_working_days",
                        "plan_calendars", "project_members", "projects", "user_roles",
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

        Role adminRole = createRole("ADMIN", "Admin", concat(PROJECT_PERMS, PLAN_MANAGE_PERMS));
        Role pmRole = createRole("PROJECT_MANAGER", "PM", concat(PROJECT_PERMS, PLAN_MANAGE_PERMS));
        Role memberRole = createRole("PROJECT_MEMBER", "Member", MEMBER_PERMS);

        createUser("admin.dep", "admin.dep@example.com", adminRole);
        User pm = createUser("pm.dep", "pm.dep@example.com", pmRole);
        User member = createUser("member.dep", "member.dep@example.com", memberRole);

        pmUserId = pm.getId();
        memberUserId = member.getId();

        projectId = createProject(adminToken(), "PRJ-DEP", "Project Dep Test");
        addMember(adminToken(), projectId, pmUserId, ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, memberUserId, ProjectMemberRole.MEMBER);
    }

    // ===================== tests =====================

    @Test
    void testDep_Create_AllTypes_PositiveLag() throws Exception {
        String pmToken = login("pm.dep", PASSWORD);
        String planId = createPlan(pmToken, "PLAN-DEP-01", "Master Dep", "MASTER");

        String a = createTask(pmToken, planId, "A", "Task A", "TASK", null);
        String b = createTask(pmToken, planId, "B", "Task B", "TASK", null);

        // FS + lag dương (PLN-AC-DEP-01, DEP-03)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + b + "/dependencies")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "predecessorTaskId": "%s",
                                  "dependencyType": "FS",
                                  "lagMinutes": 480
                                }
                                """.formatted(a)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.predecessorTaskCode").value("A"))
                .andExpect(jsonPath("$.successorTaskCode").value("B"))
                .andExpect(jsonPath("$.dependencyType").value("FS"))
                .andExpect(jsonPath("$.lagMinutes").value(480));

        // SS/FF/SF đều được (PLN-AC-DEP-03)
        for (String type : new String[] {"SS", "FF", "SF"}) {
            String other = createTask(pmToken, planId, "X" + type, "X" + type, "TASK", null);
            mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + other + "/dependencies")
                            .header("Authorization", "Bearer " + pmToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "predecessorTaskId": "%s",
                                      "dependencyType": "%s"
                                    }
                                    """.formatted(a, type)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dependencyType").value(type));
        }
    }

    @Test
    void testDep_SelfAndDuplicateRejected() throws Exception {
        String pmToken = login("pm.dep", PASSWORD);
        String planId = createPlan(pmToken, "PLAN-DEP-02", "Master Self", "MASTER");

        String a = createTask(pmToken, planId, "A", "Task A", "TASK", null);
        String b = createTask(pmToken, planId, "B", "Task B", "TASK", null);

        // Self-loop -> 400 SELF_DEPENDENCY (PLN-RULE-DEP-01)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + a + "/dependencies")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "predecessorTaskId": "%s",
                                  "dependencyType": "FS"
                                }
                                """.formatted(a)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_DEPENDENCY"));

        // Duplicate (pred, succ, type) -> 409 (PLN-RULE-DEP-05)
        createDep(pmToken, planId, a, b, "FS", null);
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + b + "/dependencies")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "predecessorTaskId": "%s",
                                  "dependencyType": "FS"
                                }
                                """.formatted(a)))
                .andExpect(status().isConflict());
    }

    @Test
    void testDep_CycleRejected() throws Exception {
        String pmToken = login("pm.dep", PASSWORD);
        String planId = createPlan(pmToken, "PLAN-DEP-03", "Master Cycle", "MASTER");

        String a = createTask(pmToken, planId, "A", "Task A", "TASK", null);
        String b = createTask(pmToken, planId, "B", "Task B", "TASK", null);
        String c = createTask(pmToken, planId, "C", "Task C", "TASK", null);

        createDep(pmToken, planId, a, b, "FS", null);
        createDep(pmToken, planId, b, c, "FS", null);

        // c -> a tạo cycle A→B→C→A -> 400 DEPENDENCY_CYCLE (PLN-AC-DEP-02)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + a + "/dependencies")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "predecessorTaskId": "%s",
                                  "dependencyType": "FS"
                                }
                                """.formatted(c)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_CYCLE"));
    }

    @Test
    void testDep_CrossProjectRejected() throws Exception {
        String pmToken = login("pm.dep", PASSWORD);
        String planA = createPlan(pmToken, "PLAN-DEP-A", "Master A", "MASTER");
        String planB = createPlan(pmToken, "PLAN-DEP-B", "Master B", "MASTER");

        String x = createTask(pmToken, planA, "X", "Task X", "TASK", null);
        String y = createTask(pmToken, planB, "Y", "Task Y", "TASK", null);

        // pred thuộc plan khác -> 400 CROSS_PROJECT_DEPENDENCY (PLN-AC-DEP-06)
        mockMvc.perform(post("/api/v1/plans/" + planB + "/tasks/" + y + "/dependencies")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "predecessorTaskId": "%s",
                                  "dependencyType": "FS"
                                }
                                """.formatted(x)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CROSS_PROJECT_DEPENDENCY"));
    }

    @Test
    void testDep_NegativeLagAllowedAndDeleteCascade() throws Exception {
        String pmToken = login("pm.dep", PASSWORD);
        String planId = createPlan(pmToken, "PLAN-DEP-05", "Master NegLag", "MASTER");

        String a = createTask(pmToken, planId, "A", "Task A", "TASK", null);
        String b = createTask(pmToken, planId, "B", "Task B", "TASK", null);
        String depId = createDep(pmToken, planId, a, b, "FS", -240);

        // lag âm được phép (PLN-AC-DEP-05, config allowNegativeLag default true)
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk());
        // verify dependency còn tồn tại qua DB count
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plan_task_dependencies WHERE id = ?::uuid", Integer.class, depId);
        org.junit.jupiter.api.Assertions.assertEquals(1, cnt);

        // Xóa task A (predecessor) -> dep bị dọn cascade (PLN-AC-DEP-04)
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + a)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        Integer afterDelete = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plan_task_dependencies WHERE id = ?::uuid", Integer.class, depId);
        org.junit.jupiter.api.Assertions.assertEquals(0, afterDelete);
    }

    @Test
    void testDep_DeleteEndpointAndAccess() throws Exception {
        String pmToken = login("pm.dep", PASSWORD);
        String memberToken = login("member.dep", PASSWORD);
        String planId = createPlan(pmToken, "PLAN-DEP-06", "Master Del", "MASTER");

        String a = createTask(pmToken, planId, "A", "Task A", "TASK", null);
        String b = createTask(pmToken, planId, "B", "Task B", "TASK", null);
        String depId = createDep(pmToken, planId, a, b, "SS", 0);

        // Xóa dependency -> 204
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + b + "/dependencies/" + depId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        // Xóa dependency không tồn tại -> 404
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + b + "/dependencies/" + depId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNotFound());

        // Member thiếu plan:update -> 403
        String dep2 = createDep(pmToken, planId, a, b, "FS", 0);
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + b + "/dependencies")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "predecessorTaskId": "%s",
                                  "dependencyType": "FS"
                                }
                                """.formatted(a)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + b + "/dependencies/" + dep2)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDep_ListEndpoint() throws Exception {
        String pmToken = login("pm.dep", PASSWORD);
        String memberToken = login("member.dep", PASSWORD);
        String planId = createPlan(pmToken, "PLAN-DEP-07", "Master List", "MASTER");

        String a = createTask(pmToken, planId, "A", "Task A", "TASK", null);
        String b = createTask(pmToken, planId, "B", "Task B", "TASK", null);
        String c = createTask(pmToken, planId, "C", "Task C", "TASK", null);
        createDep(pmToken, planId, a, b, "FS", 480);
        createDep(pmToken, planId, b, c, "SS", 0);

        // GET list dependency -> 200, đầy đủ thông tin pred/succ/type/lag
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks/dependencies")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].predecessorTaskCode").value("A"))
                .andExpect(jsonPath("$[0].successorTaskCode").value("B"))
                .andExpect(jsonPath("$[0].dependencyType").value("FS"))
                .andExpect(jsonPath("$[0].lagMinutes").value(480))
                .andExpect(jsonPath("$[1].predecessorTaskCode").value("B"))
                .andExpect(jsonPath("$[1].successorTaskCode").value("C"))
                .andExpect(jsonPath("$[1].dependencyType").value("SS"));

        // plan:view (member) đọc được danh sách dependency
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks/dependencies")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // plan không tồn tại -> 404
        mockMvc.perform(get("/api/v1/plans/" + UUID.randomUUID() + "/tasks/dependencies")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNotFound());
    }

    // ===================== helpers =====================

    private String createPlan(String token, String code, String name, String status) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "planCode": "%s",
                                  "planName": "%s",
                                  "planType": "MASTER"
                                }
                                """.formatted(projectId, code, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String createTask(String token, String planId, String code, String name, String type,
            String parentId) throws Exception {
        String parent = parentId == null ? "" : "\"parentId\":\"" + parentId + "\",";
        MvcResult res = mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskCode": "%s",
                                  "taskName": "%s",
                                  "taskType": "%s",
                                  %s
                                  "status": "NOT_STARTED"
                                }
                                """.formatted(code, name, type, parent)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String createDep(String token, String planId, String pred, String succ, String type,
            Integer lag) throws Exception {
        String lagJson = lag == null ? "" : ",\"lagMinutes\":" + lag;
        MvcResult res = mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + succ + "/dependencies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "predecessorTaskId": "%s",
                                  "dependencyType": "%s"%s
                                }
                                """.formatted(pred, type, lagJson)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String json(MvcResult res, String field) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).get(field).asText();
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
        String body = objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        return node.get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        return login("admin.dep", PASSWORD);
    }

    private String createProject(String token, String code, String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s",
                                  "startDate": "2026-08-01",
                                  "endDate": "2026-12-31"
                                }
                                """.formatted(code, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void addMember(String token, String prjId, UUID userId, ProjectMemberRole role) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + prjId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s",
                                  "role": "%s"
                                }
                                """.formatted(userId, role)))
                .andExpect(status().isCreated());
    }

    private String[] concat(String[] a, String[] b) {
        String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}