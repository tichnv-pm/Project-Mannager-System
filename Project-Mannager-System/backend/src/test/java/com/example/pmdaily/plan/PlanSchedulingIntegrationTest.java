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
 * Integration tests PLN-BE-05 — scheduling engine (docs/api/13-planning-api.md muc 2.6,
 * POST /plans/{id}/recalc). Cover PLN-AC-SCHED-01..05 (+ PLN-AC-CAL-05 qua WORKING exception):
 * forward pass theo dependency + lag + working calendar, MANUAL/FIXED_DATE giữ nguyên,
 * idempotent, constraint + warnings, milestone/summary roll-up, NO_START_ANCHOR, phân quyền plan:schedule.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanSchedulingIntegrationTest {

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

        createUser("admin.sch", "admin.sch@example.com", adminRole);
        User pm = createUser("pm.sch", "pm.sch@example.com", pmRole);
        User member = createUser("member.sch", "member.sch@example.com", memberRole);

        projectId = createProject(adminToken(), "PRJ-SCH", "Project Schedule Test");
        addMember(adminToken(), projectId, pm.getId(), ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, member.getId(), ProjectMemberRole.MEMBER);

        createDefaultCalendar(adminToken());
    }

    // ===================== tests =====================

    @Test
    void testSched_ForwardChain_Fs_Lag_WorkingDays() throws Exception {
        String pm = login("pm.sch", PASSWORD);
        String planId = createPlan(pm, "PLAN-SCH-01", "Master Chain", "2026-08-03");

        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":480}");
        String b = createTask(pm, planId, "B", "Task B", "TASK", null, "{\"durationMinutes\":480}");
        createDep(pm, planId, a, b, "FS", 0);

        // PLN-AC-SCHED-01: A Mon 03/08 (1 ngày), B FS bắt đầu Thứ 3
        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(2))
                .andExpect(jsonPath("$.scheduledTasks").value(2))
                .andExpect(jsonPath("$.plannedStart").value("2026-08-03"))
                .andExpect(jsonPath("$.plannedFinish").value("2026-08-04"));

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plannedStart").value("2026-08-03"))
                .andExpect(jsonPath("$[0].plannedFinish").value("2026-08-03"))
                .andExpect(jsonPath("$[1].plannedStart").value("2026-08-04"))
                .andExpect(jsonPath("$[1].plannedFinish").value("2026-08-04"));
    }

    @Test
    void testSched_HolidaySkipped_AndWorkingException() throws Exception {
        String admin = adminToken();
        String pm = login("pm.sch", PASSWORD);

        // exception NON_WORKING thứ Tư 05/08; WORKING thứ Bảy 08/08
        mockMvc.perform(post("/api/v1/plan-calendars/" + calendarId(admin) + "/exceptions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exceptionDate":"2026-08-05","exceptionType":"NON_WORKING","note":"lễ"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/plan-calendars/" + calendarId(admin) + "/exceptions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exceptionDate":"2026-08-08","exceptionType":"WORKING","note":"làm bù"}
                                """))
                .andExpect(status().isCreated());

        String planId = createPlan(pm, "PLAN-SCH-02", "Master Holiday", "2026-08-03");
        // A 3 ngày: 03,04,06 (bỏ 05/08 holiday); B 1 ngày: 07/08; C 1 ngày: 08/08 (WORKING exception)
        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":1440}");
        String b = createTask(pm, planId, "B", "Task B", "TASK", null, "{\"durationMinutes\":480}");
        String c = createTask(pm, planId, "C", "Task C", "TASK", null, "{\"durationMinutes\":480}");
        createDep(pm, planId, a, b, "FS", 0);
        createDep(pm, planId, b, c, "FS", 0);

        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plannedStart").value("2026-08-03"))
                .andExpect(jsonPath("$[0].plannedFinish").value("2026-08-06"))
                .andExpect(jsonPath("$[1].plannedStart").value("2026-08-07"))
                .andExpect(jsonPath("$[1].plannedFinish").value("2026-08-07"))
                .andExpect(jsonPath("$[2].plannedStart").value("2026-08-08"))
                .andExpect(jsonPath("$[2].plannedFinish").value("2026-08-08"));
    }

    @Test
    void testSched_ManualAndFixedDate_Kept() throws Exception {
        String pm = login("pm.sch", PASSWORD);
        String planId = createPlan(pm, "PLAN-SCH-03", "Manual Fixed", "2026-08-03");

        String m = createTask(pm, planId, "M", "Task M", "TASK", null,
                "{\"scheduleMode\":\"MANUAL\",\"plannedStart\":\"2026-09-01\",\"plannedFinish\":\"2026-09-03\"}");
        String f = createTask(pm, planId, "F", "Task F", "TASK", null,
                "{\"constraintType\":\"FIXED_DATE\",\"plannedStart\":\"2026-09-10\",\"plannedFinish\":\"2026-09-11\"}");
        String a = createTask(pm, planId, "A", "Auto A", "TASK", null, "{\"durationMinutes\":480}");
        String b = createTask(pm, planId, "B", "Auto B", "TASK", null, "{\"durationMinutes\":480}");
        createDep(pm, planId, m, b, "FS", 0);
        createDep(pm, planId, f, a, "FS", 0);

        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.scheduledTasks").value(2));

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                // M: MANUAL giữ nguyên ngày
                .andExpect(jsonPath("$[0].plannedStart").value("2026-09-01"))
                .andExpect(jsonPath("$[0].plannedFinish").value("2026-09-03"))
                // F: FIXED_DATE giữ nguyên ngày
                .andExpect(jsonPath("$[1].plannedStart").value("2026-09-10"))
                .andExpect(jsonPath("$[1].plannedFinish").value("2026-09-11"))
                // A (auto) sau F (finish T6 11/09): ngày làm việc kế tiếp T2 14/09
                .andExpect(jsonPath("$[2].plannedStart").value("2026-09-14"))
                .andExpect(jsonPath("$[2].plannedFinish").value("2026-09-14"))
                // B (auto) sau M: 04/09
                .andExpect(jsonPath("$[3].plannedStart").value("2026-09-04"))
                .andExpect(jsonPath("$[3].plannedFinish").value("2026-09-04"));
    }

    @Test
    void testSched_Constraint_Warnings_And_NoAnchor() throws Exception {
        String pm = login("pm.sch", PASSWORD);
        String planId = createPlan(pm, "PLAN-SCH-04", "Constraint", "2026-08-03");

        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":480}");
        String c1 = createTask(pm, planId, "C1", "No Earlier", "TASK", null,
                "{\"constraintType\":\"START_NO_EARLIER_THAN\",\"constraintDate\":\"2026-08-10\"}");
        String c2 = createTask(pm, planId, "C2", "No Later", "TASK", null,
                "{\"constraintType\":\"START_NO_LATER_THAN\",\"constraintDate\":\"2026-08-03\"}");
        createDep(pm, planId, a, c1, "FS", null);
        createDep(pm, planId, a, c2, "FS", null);

        // C1: đẩy lên 10/08 (no-earlier); C2: candidate 04/08 vi phạm no-later 03/08 -> CONSTRAINT_CONFLICT
        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedStart").value("2026-08-03"))
                .andExpect(jsonPath("$.warnings.length()").value(1))
                .andExpect(jsonPath("$.warnings[0].type").value("CONSTRAINT_CONFLICT"))
                .andExpect(jsonPath("$.warnings[0].wbsCode").value("3"));

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plannedStart").value("2026-08-03"))
                .andExpect(jsonPath("$[1].plannedStart").value("2026-08-10"))
                .andExpect(jsonPath("$[2].plannedStart").value("2026-08-04"));

        // Plan không có plannedStart + task không predecessor + không ngày -> NO_START_ANCHOR, task giữ null
        String planNoAnchor = createPlan(pm, "PLAN-SCH-05", "No Anchor", null);
        createTask(pm, planNoAnchor, "X", "Unanchored", "TASK", null, null);
        mockMvc.perform(post("/api/v1/plans/" + planNoAnchor + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledTasks").value(0))
                .andExpect(jsonPath("$.warnings[0].type").value("NO_START_ANCHOR"));
    }

    @Test
    void testSched_MilestoneAndSummary_Rollup() throws Exception {
        String pm = login("pm.sch", PASSWORD);
        String planId = createPlan(pm, "PLAN-SCH-06", "Milestone Rollup", "2026-08-03");

        String p = createTask(pm, planId, "P", "Phase 1", "PHASE", null, null);
        String l1 = createTask(pm, planId, "L1", "Leaf 1", "TASK", p, "{\"durationMinutes\":960}");
        String l2 = createTask(pm, planId, "L2", "Leaf 2", "TASK", p, "{\"durationMinutes\":480}");
        String m1 = createTask(pm, planId, "M1", "Milestone", "MILESTONE", p, null);
        createDep(pm, planId, l1, m1, "FS", null);

        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedStart").value("2026-08-03"))
                .andExpect(jsonPath("$.plannedFinish").value("2026-08-05"));

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                // summary P: start = min(child), finish = max(child)
                .andExpect(jsonPath("$[0].plannedStart").value("2026-08-03"))
                .andExpect(jsonPath("$[0].plannedFinish").value("2026-08-05"))
                // L1 2 ngày làm việc: 08/03->08/04
                .andExpect(jsonPath("$[1].plannedStart").value("2026-08-03"))
                .andExpect(jsonPath("$[1].plannedFinish").value("2026-08-04"))
                // L2 1 ngày
                .andExpect(jsonPath("$[2].plannedStart").value("2026-08-03"))
                .andExpect(jsonPath("$[2].plannedFinish").value("2026-08-03"))
                // milestone: start == finish (PLN-RULE-SCHED-08)
                .andExpect(jsonPath("$[3].plannedStart").value("2026-08-05"))
                .andExpect(jsonPath("$[3].plannedFinish").value("2026-08-05"))
                .andExpect(jsonPath("$[3].isMilestone").value(true));
    }

    @Test
    void testSched_Idempotent_Access() throws Exception {
        String pm = login("pm.sch", PASSWORD);
        String member = login("member.sch", PASSWORD);
        String planId = createPlan(pm, "PLAN-SCH-07", "Idempotent", "2026-08-03");

        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":480}");
        String b = createTask(pm, planId, "B", "Task B", "TASK", null, "{\"durationMinutes\":480}");
        createDep(pm, planId, a, b, "FS", 0);

        MvcResult first = performRecalc(pm, planId);
        MvcResult second = performRecalc(pm, planId);
        JsonNode f = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode s = objectMapper.readTree(second.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(
                f.path("plannedStart").asText(), s.path("plannedStart").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                f.path("plannedFinish").asText(), s.path("plannedFinish").asText());

        // member thiếu plan:schedule -> 403
        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());

        // 404 với plan không tồn tại
        mockMvc.perform(post("/api/v1/plans/" + UUID.randomUUID() + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
    }

    // ===================== helpers =====================

    private MvcResult performRecalc(String token, String planId) throws Exception {
        return mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
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

    private String calendarId(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    private String createPlan(String token, String code, String name, String plannedStart) throws Exception {
        String start = plannedStart == null ? "" : ",\"plannedStart\":\"" + plannedStart + "\"";
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
                                """.formatted(projectId, code, name, start)))
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
        return login("admin.sch", PASSWORD);
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