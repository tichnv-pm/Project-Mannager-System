package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.util.Map;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasItem;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests PLN-FE-10 â€” Gantt data read-only (docs/api/13-planning-api.md muc 3.3,
 * docs/planning/13 muc 5): tree + dates, critical flags live, baseline overlay, dependencies,
 * resources, phÃ¢n quyá»n view.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanGanttIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PROJECT_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};
    private static final String[] PLAN_PERMS =
            {"plan:view", "plan:create", "plan:update", "plan:delete", "plan:approve",
                    "plan:schedule", "plan:version", "plan:baseline", "plan:resource",
                    "plan:link", "plan:change", "task:create", "task:view"};
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
    private UUID pmUserId;

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
                        "plan_calendar_working_days", "plan_calendars", "attachments",
                        "task_comments", "task_assignees", "task_watchers", "task_tags",
                        "tasks", "milestones", "issues", "risks", "project_members",
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

        Role adminRole = createRole("ADMIN", "Admin", concat(PROJECT_PERMS, PLAN_PERMS));
        Role pmRole = createRole("PROJECT_MANAGER", "PM", concat(PROJECT_PERMS, PLAN_PERMS));
        Role memberRole = createRole("PROJECT_MEMBER", "Member", MEMBER_PERMS);
        Role viewerRole = createRole("VIEWER", "Viewer", new String[0]);

        createUser("admin.gt", "admin.gt@example.com", adminRole);
        User pm = createUser("pm.gt", "pm.gt@example.com", pmRole);
        User member = createUser("member.gt", "member.gt@example.com", memberRole);
        createUser("engineer.gt", "engineer.gt@example.com", viewerRole);

        projectId = createProject(adminToken(), "PRJ-GT", "Project Gantt Test");
        addMember(adminToken(), projectId, pm.getId(), ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, member.getId(), ProjectMemberRole.MEMBER);
        pmUserId = pm.getId();
        createDefaultCalendar(adminToken());
    }

    @Test
    void testGantt_TreeCriticalBaselineResourcesDependencies() throws Exception {
        String pm = login("pm.gt", PASSWORD);
        String planId = createPlan(pm, "GT-01", "2026-08-03");
        String a = createTask(pm, planId, "A", "Task A", "TASK", null,
                "{\"durationMinutes\":960,\"plannedEffortMinutes\":480,\"percentComplete\":50}");
        String b = createTask(pm, planId, "B", "Task B", "TASK", null, "{\"durationMinutes\":480}");
        createDep(pm, planId, a, b, "FS", 0);
        assign(pm, planId, a, "USER", pmUserId, 80);

        // recalc: engine tÃ­nh ngÃ y (A 03-04/08, B 05/08) â€” B káº¿t thÃºc Ä‘Ãºng planFinish (max task finish)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());

        // baseline: approve + táº¡o baseline (snapshot start/finish cá»§a A)
        submit(pm, planId);
        mockMvc.perform(post("/api/v1/plans/" + planId + "/approve")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/plans/" + planId + "/baselines")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"BL1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/plans/" + planId + "/gantt")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.planCode").value("GT-01"))
                .andExpect(jsonPath("$.plan.status").value("APPROVED"))
                .andExpect(jsonPath("$.tasks.length()").value(2))
                .andExpect(jsonPath("$..wbsCode", hasItem("1")))
                .andExpect(jsonPath("$..wbsCode", hasItem("2")))
                .andExpect(jsonPath("$.tasks[0].start").value("2026-08-03"))
                .andExpect(jsonPath("$.tasks[0].finish").value("2026-08-04"))
                .andExpect(jsonPath("$.tasks[0].percentComplete").value(50))
                .andExpect(jsonPath("$.tasks[0].resources[0].allocationPercent").value(80))
                .andExpect(jsonPath("$.tasks[0].baseline.start").value("2026-08-03"))
                .andExpect(jsonPath("$.tasks[0].isCritical").value(true))
                .andExpect(jsonPath("$.tasks[1].isCritical").value(true))
                .andExpect(jsonPath("$.dependencies.length()").value(1))
                .andExpect(jsonPath("$.dependencies[0].from").value(a))
                .andExpect(jsonPath("$.dependencies[0].to").value(b))
                .andExpect(jsonPath("$.dependencies[0].type").value("FS"))
                .andExpect(jsonPath("$.sprints").isArray())
                .andExpect(jsonPath("$.tasks[0].hasGitCommits").value(false))
                .andExpect(jsonPath("$.tasks[0].hasGitPrs").value(false));
    }

    @Test
    void testGantt_ViewAccess_And_404() throws Exception {
        String pm = login("pm.gt", PASSWORD);
        String member = login("member.gt", PASSWORD);
        String viewer = login("engineer.gt", PASSWORD);
        String planId = createPlan(pm, "GT-02", "2026-08-03");

        // member (plan:view + trong project) xem Ä‘Æ°á»£c
        mockMvc.perform(get("/api/v1/plans/" + planId + "/gantt")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(0));
        // viewer khÃ´ng thuá»™c project -> 403
        mockMvc.perform(get("/api/v1/plans/" + planId + "/gantt")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden());
        // plan khÃ´ng tá»“n táº¡i -> 404
        mockMvc.perform(get("/api/v1/plans/" + UUID.randomUUID() + "/gantt")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
        // plan khÃ´ng cÃ³ baseline/task -> tasks rá»—ng, khÃ´ng lá»—i
        mockMvc.perform(get("/api/v1/plans/" + planId + "/gantt")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(0))
                .andExpect(jsonPath("$.dependencies.length()").value(0));
    }

    // ===================== helpers =====================

    private String assign(String token, String planId, String taskId, String type, UUID resourceId, int percent)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + taskId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"" + type + "\",\"resourceId\":\"" + resourceId
                                + "\",\"allocationPercent\":" + percent + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private void submit(String token, String planId) throws Exception {
        mockMvc.perform(post("/api/v1/plans/" + planId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String createPlan(String token, String code, String start) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "planCode": "%s",
                                  "planName": "%s",
                                  "planType": "MASTER",
                                  "plannedStart": "%s"
                                }
                                """.formatted(projectId, code, code, start)))
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
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        return login("admin.gt", PASSWORD);
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