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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests PLN-BE-06 — critical path (docs/planning/09, docs/api/13-planning-api.md muc 2.1).
 * Cover PLN-AC-CP-01..05: CPM forward/backward float, threshold 0, MILESTONE + MANUAL trong critical,
 * recalc snapshot is_critical, no-dep =&gt; tất cả critical, phân quyền plan:view, 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanCriticalPathIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PROJECT_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};
    private static final String[] PLAN_MANAGE_PERMS =
            {"plan:view", "plan:create", "plan:update", "plan:delete", "plan:approve", "plan:schedule"};
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
        Role viewerRole = createRole("VIEWER", "Viewer", new String[0]);

        createUser("admin.cp", "admin.cp@example.com", adminRole);
        User pm = createUser("pm.cp", "pm.cp@example.com", pmRole);
        User member = createUser("member.cp", "member.cp@example.com", memberRole);
        createUser("viewer.cp", "viewer.cp@example.com", viewerRole);

        projectId = createProject(adminToken(), "PRJ-CP", "Project Critical Path Test");
        addMember(adminToken(), projectId, pm.getId(), ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, member.getId(), ProjectMemberRole.MEMBER);

        createDefaultCalendar(adminToken());
    }

    // ===================== tests =====================

    @Test
    void testCp_Chain_ForwardBackwardFloat() throws Exception {
        String pm = login("pm.cp", PASSWORD);
        String planId = createPlan(pm, "PLAN-CP-01", "2026-08-03");

        // A(2d) -> B(3d) -> D(1d); A -> C(1d) -> D (docs/planning/09 muc 7)
        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        String b = createTask(pm, planId, "B", "Task B", "TASK", null, "{\"durationMinutes\":1440}");
        String c = createTask(pm, planId, "C", "Task C", "TASK", null, "{\"durationMinutes\":480}");
        String d = createTask(pm, planId, "D", "Task D", "TASK", null, "{\"durationMinutes\":480}");
        createDep(pm, planId, a, b, "FS", 0);
        createDep(pm, planId, a, c, "FS", 0);
        createDep(pm, planId, b, d, "FS", 0);
        createDep(pm, planId, c, d, "FS", 0);

        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());

        // A 03/08..04/08; B 05/08..07/08; C 05/08; D 10/08 (tổng 6 ngày làm việc = 2880 phút)
        mockMvc.perform(get("/api/v1/plans/" + planId + "/critical-path")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholdMinutes").value(0))
                .andExpect(jsonPath("$.criticalTaskCount").value(3))
                .andExpect(jsonPath("$.totalDurationMinutes").value(2880))
                .andExpect(jsonPath("$.tasks.length()").value(4))

                // A: critical TF=0
                .andExpect(jsonPath("$.tasks[0].earlyStart").value("2026-08-03"))
                .andExpect(jsonPath("$.tasks[0].earlyFinish").value("2026-08-04"))
                .andExpect(jsonPath("$.tasks[0].lateStart").value("2026-08-03"))
                .andExpect(jsonPath("$.tasks[0].lateFinish").value("2026-08-04"))
                .andExpect(jsonPath("$.tasks[0].totalFloatMinutes").value(0))
                .andExpect(jsonPath("$.tasks[0].isCritical").value(true))
                .andExpect(jsonPath("$.tasks[0].criticalPathId").value(1))
                // B: critical TF=0
                .andExpect(jsonPath("$.tasks[1].earlyStart").value("2026-08-05"))
                .andExpect(jsonPath("$.tasks[1].earlyFinish").value("2026-08-07"))
                .andExpect(jsonPath("$.tasks[1].lateStart").value("2026-08-05"))
                .andExpect(jsonPath("$.tasks[1].lateFinish").value("2026-08-07"))
                .andExpect(jsonPath("$.tasks[1].totalFloatMinutes").value(0))
                .andExpect(jsonPath("$.tasks[1].isCritical").value(true))
                .andExpect(jsonPath("$.tasks[1].criticalPathId").value(1))
                // C: float 2 ngày làm việc -> không critical
                .andExpect(jsonPath("$.tasks[2].earlyStart").value("2026-08-05"))
                .andExpect(jsonPath("$.tasks[2].earlyFinish").value("2026-08-05"))
                .andExpect(jsonPath("$.tasks[2].lateStart").value("2026-08-07"))
                .andExpect(jsonPath("$.tasks[2].lateFinish").value("2026-08-07"))
                .andExpect(jsonPath("$.tasks[2].totalFloatMinutes").value(960))
                .andExpect(jsonPath("$.tasks[2].isCritical").value(false))
                // D: critical TF=0
                .andExpect(jsonPath("$.tasks[3].earlyStart").value("2026-08-10"))
                .andExpect(jsonPath("$.tasks[3].earlyFinish").value("2026-08-10"))
                .andExpect(jsonPath("$.tasks[3].totalFloatMinutes").value(0))
                .andExpect(jsonPath("$.tasks[3].isCritical").value(true))
                .andExpect(jsonPath("$.tasks[3].criticalPathId").value(1));
    }

    @Test
    void testCp_Milestone_Included() throws Exception {
        String pm = login("pm.cp", PASSWORD);
        String planId = createPlan(pm, "PLAN-CP-02", "2026-08-03");

        String l1 = createTask(pm, planId, "L1", "Leaf 1", "TASK", null, "{\"durationMinutes\":960}");
        String m1 = createTask(pm, planId, "M1", "Milestone", "MILESTONE", null, null);
        createDep(pm, planId, l1, m1, "FS", 0);

        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/plans/" + planId + "/critical-path")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticalTaskCount").value(2))
                // L1
                .andExpect(jsonPath("$.tasks[0].earlyStart").value("2026-08-03"))
                .andExpect(jsonPath("$.tasks[0].totalFloatMinutes").value(0))
                .andExpect(jsonPath("$.tasks[0].isCritical").value(true))
                // milestone tính float như task thường (PLN-RULE-CP-03)
                .andExpect(jsonPath("$.tasks[1].taskType").value("MILESTONE"))
                .andExpect(jsonPath("$.tasks[1].earlyStart").value("2026-08-05"))
                .andExpect(jsonPath("$.tasks[1].earlyFinish").value("2026-08-05"))
                .andExpect(jsonPath("$.tasks[1].totalFloatMinutes").value(0))
                .andExpect(jsonPath("$.tasks[1].isCritical").value(true));
    }

    @Test
    void testCp_Manual_Included() throws Exception {
        String pm = login("pm.cp", PASSWORD);
        String planId = createPlan(pm, "PLAN-CP-03", "2026-08-03");

        // MANUAL đã chốt ngày 01/09..03/09 -> A auto 04/09
        String m2 = createTask(pm, planId, "M2", "Manual", "TASK", null,
                "{\"scheduleMode\":\"MANUAL\",\"plannedStart\":\"2026-09-01\",\"plannedFinish\":\"2026-09-03\"}");
        String a = createTask(pm, planId, "A", "Auto A", "TASK", null, "{\"durationMinutes\":480}");
        createDep(pm, planId, m2, a, "FS", 0);

        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());

        // PLN-AC-CP-03: MANUAL nằm trên đường critical tính như task thường
        mockMvc.perform(get("/api/v1/plans/" + planId + "/critical-path")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticalTaskCount").value(2))
                .andExpect(jsonPath("$.tasks[0].isCritical").value(true))
                .andExpect(jsonPath("$.tasks[0].totalFloatMinutes").value(0))
                .andExpect(jsonPath("$.tasks[1].earlyStart").value("2026-09-04"))
                .andExpect(jsonPath("$.tasks[1].isCritical").value(true))
                .andExpect(jsonPath("$.tasks[1].totalFloatMinutes").value(0));
    }

    @Test
    void testCp_ChainEndLeaf_BacksOffDuration() throws Exception {
        // Regression: task cuối chain (không successor) phải back-off LS = LF - (duration-1).
        // Trước fix, LS bị gán = planFinish -> float = cả duration -> không bao giờ critical
        // (demo b01 Master Plan lộ ra: c10 dài 130 ngày, EF = planFinish 4/13/2027, float 62k).
        String pm = login("pm.cp", PASSWORD);
        String planId = createPlan(pm, "PLAN-CP-07", "2026-08-03");

        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        String b = createTask(pm, planId, "B", "Task B", "TASK", null, "{\"durationMinutes\":1440}");
        createDep(pm, planId, a, b, "FS", 0);

        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());

        // A 03/08..04/08; B 05/08..07/08 (kết thúc đúng plan finish — rollup)
        mockMvc.perform(get("/api/v1/plans/" + planId + "/critical-path")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticalTaskCount").value(2))
                // B (chain-end, 3 ngày): LS phải lùi về start 05/08
                .andExpect(jsonPath("$.tasks[1].earlyStart").value("2026-08-05"))
                .andExpect(jsonPath("$.tasks[1].earlyFinish").value("2026-08-07"))
                .andExpect(jsonPath("$.tasks[1].lateStart").value("2026-08-05"))
                .andExpect(jsonPath("$.tasks[1].lateFinish").value("2026-08-07"))
                .andExpect(jsonPath("$.tasks[1].totalFloatMinutes").value(0))
                .andExpect(jsonPath("$.tasks[1].isCritical").value(true))
                .andExpect(jsonPath("$.tasks[1].criticalPathId").value(1))
                // A (chain-start): TF = 0
                .andExpect(jsonPath("$.tasks[0].earlyStart").value("2026-08-03"))
                .andExpect(jsonPath("$.tasks[0].earlyFinish").value("2026-08-04"))
                .andExpect(jsonPath("$.tasks[0].lateStart").value("2026-08-03"))
                .andExpect(jsonPath("$.tasks[0].lateFinish").value("2026-08-04"))
                .andExpect(jsonPath("$.tasks[0].totalFloatMinutes").value(0))
                .andExpect(jsonPath("$.tasks[0].isCritical").value(true))
                .andExpect(jsonPath("$.tasks[0].criticalPathId").value(1));
    }

    @Test
    void testCp_NoDeps_AllCritical() throws Exception {
        String pm = login("pm.cp", PASSWORD);
        String planId = createPlan(pm, "PLAN-CP-04", "2026-08-03");

        createTask(pm, planId, "T1", "Task 1", "TASK", null, "{\"durationMinutes\":480}");
        createTask(pm, planId, "T2", "Task 2", "TASK", null, "{\"durationMinutes\":480}");

        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());

        // PLN-AC-CP-05: không dep -> mọi task critical (float 0)
        mockMvc.perform(get("/api/v1/plans/" + planId + "/critical-path")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticalTaskCount").value(2))
                .andExpect(jsonPath("$.tasks[0].isCritical").value(true))
                .andExpect(jsonPath("$.tasks[0].totalFloatMinutes").value(0))
                .andExpect(jsonPath("$.tasks[1].isCritical").value(true))
                .andExpect(jsonPath("$.tasks[1].totalFloatMinutes").value(0));
    }

    @Test
    void testCp_RecalcSnapshot_PersistsIsCritical() throws Exception {
        String pm = login("pm.cp", PASSWORD);
        String planId = createPlan(pm, "PLAN-CP-05", "2026-08-03");

        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        String b = createTask(pm, planId, "B", "Task B", "TASK", null, "{\"durationMinutes\":1440}");
        String c = createTask(pm, planId, "C", "Task C", "TASK", null, "{\"durationMinutes\":480}");
        String d = createTask(pm, planId, "D", "Task D", "TASK", null, "{\"durationMinutes\":480}");
        createDep(pm, planId, a, b, "FS", 0);
        createDep(pm, planId, a, c, "FS", 0);
        createDep(pm, planId, b, d, "FS", 0);
        createDep(pm, planId, c, d, "FS", 0);

        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());

        // PLN-AC-CP-04: recalc xong -> snapshot is_critical trên plan_tasks (A,B,D critical; C không)
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isCritical").value(true))
                .andExpect(jsonPath("$[1].isCritical").value(true))
                .andExpect(jsonPath("$[2].isCritical").value(false))
                .andExpect(jsonPath("$[3].isCritical").value(true));
    }

    @Test
    void testCp_Empty_And_Access() throws Exception {
        String pm = login("pm.cp", PASSWORD);
        String member = login("member.cp", PASSWORD);
        String viewer = login("viewer.cp", PASSWORD);

        // plan chưa có task -> 200 rỗng
        String emptyPlan = createPlan(pm, "PLAN-CP-06", null);
        mockMvc.perform(get("/api/v1/plans/" + emptyPlan + "/critical-path")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(0))
                .andExpect(jsonPath("$.criticalTaskCount").value(0));

        // member (plan:view) -> 200; người KHÔNG có plan:view -> 403
        mockMvc.perform(get("/api/v1/plans/" + emptyPlan + "/critical-path")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/plans/" + emptyPlan + "/critical-path")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden());

        // 404 plan không tồn tại
        mockMvc.perform(get("/api/v1/plans/" + UUID.randomUUID() + "/critical-path")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
    }

    // ===================== helpers =====================

    private String createPlan(String token, String code, String start) throws Exception {
        String startJson = start == null ? "" : ",\"plannedStart\":\"" + start + "\"";
        MvcResult res = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "planCode": "%s",
                                  "planName": "%s",
                                  "planType": "MASTER"%s
                                }
                                """.formatted(projectId, code, code, startJson)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String createTask(String token, String planId, String code, String name, String type,
            String parentId, String extra) throws Exception {
        StringBuilder body = new StringBuilder();
        body.append("{\"taskCode\":\"").append(code)
                .append("\",\"taskName\":\"").append(name)
                .append("\",\"taskType\":\"").append(type).append("\"");
        if (parentId != null) {
            body.append(",\"parentId\":\"").append(parentId).append("\"");
        }
        if (extra != null) {
            String fields = extra;
            if (fields.startsWith("{") && fields.endsWith("}")) {
                fields = fields.substring(1, fields.length() - 1);
            }
            body.append(',').append(fields);
        }
        body.append('}');
        MvcResult res = mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String createDep(String token, String planId, String pred, String succ, String type, Integer lag)
            throws Exception {
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

    private String createDefaultCalendar(String token) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Org Standard",
                                  "workingDays": [
                                    {"dayOfWeek": 1, "isWorking": true},
                                    {"dayOfWeek": 2, "isWorking": true},
                                    {"dayOfWeek": 3, "isWorking": true},
                                    {"dayOfWeek": 4, "isWorking": true},
                                    {"dayOfWeek": 5, "isWorking": true},
                                    {"dayOfWeek": 6, "isWorking": false},
                                    {"dayOfWeek": 7, "isWorking": false}
                                  ]
                                }
                                """))
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
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        return login("admin.cp", PASSWORD);
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
        return json(res, "id");
    }

    private void addMember(String token, String prjId, UUID userId, ProjectMemberRole role) throws Exception {
        mockMvc.perform(post("/api/v1/projects" + "/" + prjId + "/members")
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