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

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests PLN-BE-08 — version & baseline (docs/planning/11, docs/api/13-planning-api.md muc 2.5).
 * Cover PLN-AC-VERSION-01..03, PLN-AC-BASE-01..05: versionNo tăng + snapshot, diff v1 vs v2, baseline chỉ
 * APPROVED, bất biến + num đơn điệu kể cả xóa, variance start/finish/duration/effort/progress, milestone done.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanBaselineIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PROJECT_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};
    private static final String[] PLAN_MANAGE_PERMS =
            {"plan:view", "plan:create", "plan:update", "plan:delete", "plan:approve",
                    "plan:schedule", "plan:version", "plan:baseline", "plan:resource"};
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
                        "plan_baseline_tasks", "plan_baselines", "resource_capacities",
                        "plan_task_resources", "plan_task_dependencies", "plan_tasks",
                        "plan_versions", "project_plans", "plan_calendar_exceptions",
                        "plan_calendar_working_days", "plan_calendars", "project_members",
                        "projects", "user_roles", "role_permissions", "permissions",
                        "roles", "users"
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

        createUser("admin.bl", "admin.bl@example.com", adminRole);
        User pm = createUser("pm.bl", "pm.bl@example.com", pmRole);
        User member = createUser("member.bl", "member.bl@example.com", memberRole);
        createUser("viewer.bl", "viewer.bl@example.com", viewerRole);
        createUser("engineer.bl", "engineer.bl@example.com", viewerRole);

        projectId = createProject(adminToken(), "PRJ-BL", "Project Baseline Test");
        addMember(adminToken(), projectId, pm.getId(), ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, member.getId(), ProjectMemberRole.MEMBER);

        createDefaultCalendar(adminToken());
    }

    // ===================== tests =====================

    @Test
    void testVer_Create_Increment_And_Active() throws Exception {
        String pm = login("pm.bl", PASSWORD);
        String planId = createPlan(pm, "VER-01", "2026-08-03");
        createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);

        // PLN-AC-VERSION-01: versionNo = max+1 (v1 tự tạo lúc tạo plan)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/versions")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Sau khi có task\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.taskCount").value(1))
                .andExpect(jsonPath("$.isActive").value(true));

        // PLN-AC-VERSION-02/04: chỉ 1 ACTIVE; danh sách desc
        mockMvc.perform(get("/api/v1/plans/" + planId + "/versions")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNo").value(2))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].isActive").value(true))
                .andExpect(jsonPath("$[0].taskCount").value(1))
                .andExpect(jsonPath("$[1].versionNo").value(1))
                .andExpect(jsonPath("$[1].status").value("INACTIVE"));
    }

    @Test
    void testVer_Diff_TaskAdded_Update_And_404() throws Exception {
        String pm = login("pm.bl", PASSWORD);
        String planId = createPlan(pm, "VER-02", "2026-08-03");
        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);
        createVersion(pm, planId); // v2: snapshot có task A

        // Sửa duration 960 -> 480 rồi tạo v3
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + a)
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"Task A\",\"durationMinutes\":480,\"percentComplete\":0,\"version\":"
                                + taskVersion(pm, planId, a) + "}"))
                .andExpect(status().isOk());
        createVersion(pm, planId); // v3

        // diff v2 vs v3: durationMinutes 960 -> 480 (PLN-AC-VERSION-03)
        mockMvc.perform(get("/api/v1/plans/" + planId + "/versions/2/diff")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.compareToVersionNo").value(3))
                .andExpect(jsonPath("$.tasks[?(@.wbsCode == '1')].wbsCode").exists())
                .andExpect(jsonPath("$.tasks[?(@.field == 'durationMinutes')].fromValue", contains("960")))
                .andExpect(jsonPath("$.tasks[?(@.field == 'durationMinutes')].toValue").value(contains("480")));

        // diff v1 vs v2: task A mới (TASK_ADDED)
        mockMvc.perform(get("/api/v1/plans/" + planId + "/versions/1/diff")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[?(@.field == 'TASK_ADDED')]").exists());

        // diff version mới nhất -> 404
        mockMvc.perform(get("/api/v1/plans/" + planId + "/versions/3/diff")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
    }

    @Test
    void testBase_Requires_Approved() throws Exception {
        String pm = login("pm.bl", PASSWORD);
        String planId = createPlan(pm, "BASE-01", "2026-08-03");
        createTask(pm, planId, "T1", "Task 1", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);

        // PLN-RULE-BASE-01: DRAFT -> 400
        mockMvc.perform(post("/api/v1/plans/" + planId + "/baselines")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"cmp1\"}"))
                .andExpect(status().isBadRequest());
        submit(pm, planId);
        // SUBMITTED -> 400
        mockMvc.perform(post("/api/v1/plans/" + planId + "/baselines")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"cmp1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testBase_Snapshot_Num_Monotonic_And_SoftDelete() throws Exception {
        String pm = login("pm.bl", PASSWORD);
        String planId = createPlan(pm, "BASE-02", "2026-08-03");
        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);
        approve(pm, planId);

        String b1 = createBaseline(pm, planId, "cmp1");
        String b2 = createBaseline(pm, planId, "cmp2");

        mockMvc.perform(get("/api/v1/plans/" + planId + "/baselines")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].baselineNum").value(2))
                .andExpect(jsonPath("$[0].taskCount").value(1))
                .andExpect(jsonPath("$[1].baselineNum").value(1))
                .andExpect(jsonPath("$[1].taskCount").value(1));

        // baseline bất biến: sửa task + tạo baseline mới không ảnh hưởng b1
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + a)
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"Task A\",\"durationMinutes\":480,\"percentComplete\":0,\"version\":"
                                + taskVersion(pm, planId, a) + "}"))
                .andExpect(status().isOk());
        createBaseline(pm, planId, "cmp3");

        // Xóa baseline 2 (soft-delete) -> b3 vẫn num 3, num tăng đơn điệu kể cả khi xóa
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/baselines/" + b2)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/" + planId + "/baselines")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].baselineNum").value(3))
                .andExpect(jsonPath("$[1].baselineNum").value(1));

        // b1 vẫn còn nguyên sau khi plan thay đổi (PLN-RULE-BASE-05) — taskCount snapshot cũ
        mockMvc.perform(get("/api/v1/plans/" + planId + "/baselines/1/variance")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].baselineDurationMinutes").value(960));
    }

    @Test
    void testBase_Variance_Dates_Effort_Milestone() throws Exception {
        String pm = login("pm.bl", PASSWORD);
        String planId = createPlan(pm, "BASE-03", "2026-08-03");
        // A: MANUAL sẵn ngày 03/08..04/08 (để update sau này giữ ngày sau recalc)
        String a = createTask(pm, planId, "A", "Task A", "TASK", null,
                "{\"scheduleMode\":\"MANUAL\",\"plannedStart\":\"2026-08-03\",\"plannedFinish\":\"2026-08-04\","
                        + "\"durationMinutes\":960,\"plannedEffortMinutes\":480}");
        String m = createTask(pm, planId, "M", "Milestone", "MILESTONE", null, null);
        createDep(pm, planId, a, m, "FS", 0);
        recalc(pm, planId);
        approve(pm, planId);
        createBaseline(pm, planId, "cmp");

        // Thay đổi current: A sang 01/09..02/09 + duration 480 + progress 50; M hoàn thành
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + a)
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"Task A\",\"scheduleMode\":\"MANUAL\",\"plannedStart\":\"2026-09-01\","
                                + "\"plannedFinish\":\"2026-09-02\",\"durationMinutes\":480,"
                                + "\"percentComplete\":50,\"version\":" + taskVersion(pm, planId, a) + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + m)
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"Milestone\",\"percentComplete\":100,\"version\":"
                                + taskVersion(pm, planId, m) + "}"))
                .andExpect(status().isOk());
        recalc(pm, planId);

        mockMvc.perform(get("/api/v1/plans/" + planId + "/baselines/1/variance")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineNum").value(1))
                .andExpect(jsonPath("$.tasks.length()").value(2))
                // A: start var 09-01 - 08-03 (29 ngày); finish 09-02 - 08-04; duration -480; progress +50
                .andExpect(jsonPath("$.tasks[0].wbsCode").value("1"))
                .andExpect(jsonPath("$.tasks[0].baselineStart").value("2026-08-03"))
                .andExpect(jsonPath("$.tasks[0].currentStart").value("2026-09-01"))
                .andExpect(jsonPath("$.tasks[0].startDifferenceDays").value(29))
                .andExpect(jsonPath("$.tasks[0].finishDifferenceDays").value(29))
                .andExpect(jsonPath("$.tasks[0].durationDifferenceMinutes").value(-480))
                .andExpect(jsonPath("$.tasks[0].progressDifference").value(50))
                // M: milestone chưa đạt (baseline 0) -> hiện tại 100 => milestoneDone
                .andExpect(jsonPath("$.tasks[1].taskType").value("MILESTONE"))
                .andExpect(jsonPath("$.tasks[1].milestoneDone").value(true))
                .andExpect(jsonPath("$.tasks[1].progressDifference").value(100));
    }

    @Test
    void testBase_Variance_TaskDeleted() throws Exception {
        String pm = login("pm.bl", PASSWORD);
        String planId = createPlan(pm, "BASE-04", "2026-08-03");
        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);
        approve(pm, planId);
        createBaseline(pm, planId, "before delete");

        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + a)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/" + planId + "/baselines/1/variance")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].taskDeleted").value(true))
                .andExpect(jsonPath("$.tasks[0].currentStart").doesNotExist())
                .andExpect(jsonPath("$.tasks[0].baselineStart").value("2026-08-03"));
    }

    @Test
    void testBase_Access_Version_And_404() throws Exception {
        String pm = login("pm.bl", PASSWORD);
        String member = login("member.bl", PASSWORD);
        String viewer = login("viewer.bl", PASSWORD);
        String planId = createPlan(pm, "BASE-05", "2026-08-03");
        createTask(pm, planId, "T1", "Task 1", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);
        approve(pm, planId);
        createBaseline(pm, planId, "Baseline 1");

        // member (plan:view) xem được list/variance; không tạo baseline/version
        mockMvc.perform(get("/api/v1/plans/" + planId + "/baselines")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/plans/" + planId + "/baselines/1/variance")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/plans/" + planId + "/baselines")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/plans/" + planId + "/versions")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // viewer không có plan:view -> 403
        mockMvc.perform(get("/api/v1/plans/" + planId + "/baselines")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden());

        // baseline num không tồn tại -> 404; plan không tồn tại -> 404
        mockMvc.perform(get("/api/v1/plans/" + planId + "/baselines/99/variance")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/plans/" + UUID.randomUUID() + "/baselines")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
    }

    // ===================== helpers =====================

    private String createBaseline(String token, String planId, String description) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/plans/" + planId + "/baselines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "baselineNum");
    }

    private void createVersion(String token, String planId) throws Exception {
        mockMvc.perform(post("/api/v1/plans/" + planId + "/versions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    private void submit(String token, String planId) throws Exception {
        mockMvc.perform(post("/api/v1/plans/" + planId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void approve(String token, String planId) throws Exception {
        submit(token, planId);
        mockMvc.perform(post("/api/v1/plans/" + planId + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void recalc(String token, String planId) throws Exception {
        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

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

    private String taskVersion(String token, String planId, String taskId) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = objectMapper.readTree(res.getResponse().getContentAsString());
        return findNodeVersion(tree, taskId);
    }

    private String findNodeVersion(JsonNode node, String taskId) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                String v = findNodeVersion(child, taskId);
                if (v != null) {
                    return v;
                }
            }
            return null;
        }
        if (taskId.equals(node.path("id").asText())) {
            return node.path("version").asText();
        }
        JsonNode children = node.get("children");
        return children == null ? null : findNodeVersion(children, taskId);
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
        return login("admin.bl", PASSWORD);
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