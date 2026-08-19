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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests PLN-BE-09 — change history / change suggestion & plan links (docs/planning/02 muc 2.10-2.11,
 * docs/api/13-planning-api.md muc 2.8-2.9). Cover PLN-AC-CHG-01..04 (+02b dual approve) và PLN-AC-LINK-01..03,06.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanChangeLinkIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PROJECT_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};
    private static final String[] PLAN_MANAGE_PERMS =
            {"plan:view", "plan:create", "plan:update", "plan:delete", "plan:approve",
                    "plan:schedule", "plan:version", "plan:baseline", "plan:resource",
                    "plan:link", "plan:change",
                    "task:create", "task:view", "issue:manage", "risk:manage", "milestone:manage"};
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

        Role adminRole = createRole("ADMIN", "Admin", concat(PROJECT_PERMS, PLAN_MANAGE_PERMS));
        Role pmRole = createRole("PROJECT_MANAGER", "PM", concat(PROJECT_PERMS, PLAN_MANAGE_PERMS));
        Role memberRole = createRole("PROJECT_MEMBER", "Member", MEMBER_PERMS);
        Role viewerRole = createRole("VIEWER", "Viewer", new String[0]);

        createUser("admin.cl", "admin.cl@example.com", adminRole);
        User pm = createUser("pm.cl", "pm.cl@example.com", pmRole);
        User member = createUser("member.cl", "member.cl@example.com", memberRole);
        createUser("engineer.cl", "engineer.cl@example.com", viewerRole);

        projectId = createProject(adminToken(), "PRJ-CL", "Project Change Link Test");
        addMember(adminToken(), projectId, pm.getId(), ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, member.getId(), ProjectMemberRole.MEMBER);
        pmUserId = pm.getId();
        createDefaultCalendar(adminToken());
    }

    @Test
    void testLink_CreatePrimaryRelatedAndRules() throws Exception {
        String pm = login("pm.cl", PASSWORD);
        String planId = createPlan(pm, "CL-01", "2026-08-03");
        String a = createTask(pm, planId, "A", "Plan Task A", "TASK", null, "{\"durationMinutes\":960}");
        String b = createTask(pm, planId, "B", "Plan Task B", "TASK", null, "{\"durationMinutes\":960}");
        String et1 = createExecutionTask(pm, "ET-01");
        String et2 = createExecutionTask(pm, "ET-02");

        String linkPrimary = link(pm, planId, a, "EXECUTION_TASK", et1, "RELATED", true);
        String linkRelated = link(pm, planId, b, "EXECUTION_TASK", et1, "RELATED", false);
        // duplicate (task, targetType, target) -> 409
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLink("EXECUTION_TASK", et1, "RELATED", false)))
                .andExpect(status().isConflict());
        // execution task đã là primary của A -> không làm primary cho B (PLN-AC-LINK-02)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + b + "/links")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLink("EXECUTION_TASK", et1, "RELATED", true)))
                .andExpect(status().isConflict());
        // task đã có primary -> không thêm primary khác
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLink("EXECUTION_TASK", et2, "RELATED", true)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(linkPrimary))
                .andExpect(jsonPath("$[0].isPrimaryExecution").value(true))
                .andExpect(jsonPath("$[0].createdBy").isNotEmpty());

        // soft delete link phụ
        mockMvc.perform(delete("/api/v1/links/" + linkRelated)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks/" + b + "/links")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/v1/links/" + linkRelated)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
    }

    @Test
    void testLink_ValidPairRulesAndPermissions() throws Exception {
        String pm = login("pm.cl", PASSWORD);
        String planId = createPlan(pm, "CL-02", "2026-08-03");
        String a = createTask(pm, planId, "A", "Plan Task A", "TASK", null, "{\"durationMinutes\":960}");
        String issue = createIssue(pm, "ISSUE-01");
        String risk = createRisk(pm, "R-01");
        String milestone = createMilestone(pm, "M-01");

        // BLOCKED_BY chỉ dành cho Issue/Risk -> Milestone 400 (PLN-FR-LINK-06)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLink("MILESTONE", milestone, "BLOCKED_BY", false)))
                .andExpect(status().isBadRequest());
        // isPrimaryExecution chỉ EXECUTION_TASK -> 400
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLink("ISSUE", issue, "RELATED", true)))
                .andExpect(status().isBadRequest());
        // target không tồn tại -> 404 (PLN-AC-LINK-03)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLink("ISSUE", String.valueOf(UUID.randomUUID()), "RELATED", false)))
                .andExpect(status().isNotFound());
        // target thuộc project khác -> 400
        String otherProject = createProject(pm, "PRJ-OTHER", "Other Project");
        addMember(adminToken(), otherProject, pmUserId, ProjectMemberRole.PROJECT_MANAGER);
        String otherIssue = mockMvc.perform(post("/api/v1/issues")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + otherProject + "\",\"title\":\"Issue other\","
                                + "\"severity\":\"LOW\",\"ownerId\":\"" + pmUserId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        otherIssue = objectMapper.readTree(otherIssue).path("id").asText();
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLink("ISSUE", otherIssue, "RELATED", false)))
                .andExpect(status().isBadRequest());

        String blocked = link(pm, planId, a, "ISSUE", issue, "BLOCKED_BY", false);
        link(pm, planId, a, "RISK", risk, "RELATED", false);
        link(pm, planId, a, "MILESTONE", milestone, "RELATED", false);

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].linkType").value("BLOCKED_BY"))
                .andExpect(jsonPath("$[0].targetType").value("ISSUE"));

        // quyền: member (plan:view) xem được, không tạo/xóa; viewer không xem
        String member = login("member.cl", PASSWORD);
        String viewer = login("engineer.cl", PASSWORD);
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLink("ISSUE", issue, "RELATED", false)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks/" + a + "/links")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/links/" + blocked)
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());
    }

    @Test
    void testChangeHistory_RecordsAfterApproveOnly() throws Exception {
        String pm = login("pm.cl", PASSWORD);
        String planId = createPlan(pm, "CL-03", "2026-08-03");
        String a = createTask(pm, planId, "A", "Task A", "TASK", null,
                "{\"durationMinutes\":960,\"plannedEffortMinutes\":480}");
        String b = createTask(pm, planId, "B", "Task B", "TASK", null, "{\"durationMinutes\":480}");
        String depId = createDep(pm, planId, a, b, "FS", 0);
        approve(pm, planId);

        // sau APPROVED mới có history (PLN-AC-CHG-01/04)
        mockMvc.perform(get("/api/v1/plans/" + planId + "/change-histories")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + a)
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"Task A\",\"plannedStart\":\"2026-09-01\","
                                + "\"plannedFinish\":\"2026-09-02\",\"durationMinutes\":960,"
                                + "\"plannedEffortMinutes\":480,\"percentComplete\":25,\"version\":"
                                + taskVersion(pm, planId, a) + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + b + "/dependencies/" + depId)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/" + planId + "/change-histories")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].fieldChanged", hasItem("plannedStart")))
                .andExpect(jsonPath("$[*].fieldChanged", hasItem("percentComplete")))
                .andExpect(jsonPath("$[*].entityType", hasItem("PLAN_DEPENDENCY")))
                .andExpect(jsonPath("$[*].oldValue").isNotEmpty())
                .andExpect(jsonPath("$[*].changedBy").isNotEmpty());
    }

    @Test
    void testSuggestion_Approve_AppliesChanges_RejectNoApply() throws Exception {
        String pm = login("pm.cl", PASSWORD);
        String planId = createPlan(pm, "CL-04", "2026-08-03");
        String a = createTask(pm, planId, "A", "Task A", "TASK", null,
                "{\"durationMinutes\":960,\"plannedEffortMinutes\":480}");
        approve(pm, planId);

        String suggestionId = createSuggestion(pm, planId, a,
                "{\"entityType\":\"PLAN_TASK\",\"entityId\":\"" + a
                        + "\",\"field\":\"percentComplete\",\"oldValue\":\"0\",\"newValue\":\"50\"}");

        mockMvc.perform(post("/api/v1/change-suggestions/" + suggestionId + "/accept")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
        mockMvc.perform(get("/api/v1/plans/" + planId + "/change-histories")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].changeType").value("SUGGESTION_APPLIED"))
                .andExpect(jsonPath("$[0].changeRequestId").value(suggestionId))
                .andExpect(jsonPath("$[0].changedBy").isNotEmpty());
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(a))
                .andExpect(jsonPath("$[0].percentComplete").value(50));

        // reject -> không apply (PLN-AC-CHG-03)
        String suggestion2 = createSuggestion(pm, planId, a,
                "{\"entityType\":\"PLAN_TASK\",\"entityId\":\"" + a
                        + "\",\"field\":\"percentComplete\",\"oldValue\":\"50\",\"newValue\":\"90\"}");
        mockMvc.perform(post("/api/v1/change-suggestions/" + suggestion2 + "/reject")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].percentComplete").value(50));
        mockMvc.perform(post("/api/v1/change-suggestions/" + suggestion2 + "/reject")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/v1/change-suggestions/" + suggestion2 + "/accept")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testSuggestion_DualApprove_When_EffortOverThreshold() throws Exception {
        String pm = login("pm.cl", PASSWORD);
        String admin = login("admin.cl", PASSWORD);
        String planId = createPlan(pm, "CL-05", "2026-08-03");
        String a = createTask(pm, planId, "A", "Task A", "TASK", null,
                "{\"durationMinutes\":960,\"plannedEffortMinutes\":6000}");
        createTask(pm, planId, "B", "Task B", "TASK", null,
                "{\"durationMinutes\":960,\"plannedEffortMinutes\":5000}");
        approve(pm, planId);

        String suggestionId = createSuggestion(pm, planId, a,
                "{\"entityType\":\"PLAN_TASK\",\"entityId\":\"" + a
                        + "\",\"field\":\"plannedStart\",\"oldValue\":\"2026-08-03\",\"newValue\":\"2026-08-10\"}");

        // accept lần 1 (PM) -> vẫn PENDING chờ người thứ 2 (PLN-AC-CHG-02b)
        mockMvc.perform(post("/api/v1/change-suggestions/" + suggestionId + "/accept")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reviewedBy").isNotEmpty());
        // cùng người duyệt -> 409
        mockMvc.perform(post("/api/v1/change-suggestions/" + suggestionId + "/accept")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isConflict());
        // ADMIN duyệt lần 2 -> APPLIED + task được đổi
        mockMvc.perform(post("/api/v1/change-suggestions/" + suggestionId + "/accept")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.reviewedBy2").isNotEmpty());
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plannedStart").value("2026-08-10"));
    }

    @Test
    void testSuggestion_Permissions_And_Errors() throws Exception {
        String pm = login("pm.cl", PASSWORD);
        String member = login("member.cl", PASSWORD);
        String planId = createPlan(pm, "CL-06", "2026-08-03");
        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");

        mockMvc.perform(get("/api/v1/plans/" + planId + "/change-histories")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/plans/" + planId + "/change-suggestions")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hack\",\"description\":\"x\",\"suggestedChanges\":[]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/change-suggestions/" + UUID.randomUUID() + "/accept")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
        // task đề xuất thuộc plan khác -> 400 khi accept
        String plan2 = createPlan(pm, "CL-07", "2026-08-03");
        createTask(pm, plan2, "T2", "Task T2", "TASK", null, "{\"durationMinutes\":960}");
        String suggestion = createSuggestion(pm, plan2, a,
                "{\"entityType\":\"PLAN_TASK\",\"entityId\":\"" + a
                        + "\",\"field\":\"percentComplete\",\"oldValue\":\"0\",\"newValue\":\"50\"}");
        mockMvc.perform(post("/api/v1/change-suggestions/" + suggestion + "/accept")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLink_DeleteCreatesHistory_AfterApprove() throws Exception {
        String pm = login("pm.cl", PASSWORD);
        String planId = createPlan(pm, "CL-08", "2026-08-03");
        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        approve(pm, planId);
        String et = createExecutionTask(pm, "ET-03");
        String linkId = link(pm, planId, a, "EXECUTION_TASK", et, "RELATED", false);
        mockMvc.perform(delete("/api/v1/links/" + linkId)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/plans/" + planId + "/change-histories")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].changeType", hasItem("PLAN_LINK_ADDED")))
                .andExpect(jsonPath("$[*].changeType", hasItem("PLAN_LINK_REMOVED")));
    }

    @Test
    void testSuggestion_ListEndpoint_And_ViewAccess() throws Exception {
        String pm = login("pm.cl", PASSWORD);
        String member = login("member.cl", PASSWORD);
        String viewer = login("engineer.cl", PASSWORD);
        String planId = createPlan(pm, "CL-09", "2026-08-03");
        String a = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        approve(pm, planId);
        createSuggestion(pm, planId, a,
                "{\"entityType\":\"PLAN_TASK\",\"entityId\":\"" + a
                        + "\",\"field\":\"percentComplete\",\"oldValue\":\"0\",\"newValue\":\"50\"}");

        // PM: GET list suggestions (bổ sung read-only cho PLN-FE-08)
        mockMvc.perform(get("/api/v1/plans/" + planId + "/change-suggestions")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].title").value("Task thay đổi"));
        // member (plan:view + member project) xem được
        mockMvc.perform(get("/api/v1/plans/" + planId + "/change-suggestions")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());
        // viewer không thuộc project -> 403
        mockMvc.perform(get("/api/v1/plans/" + planId + "/change-suggestions")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden());
        // plan không tồn tại -> 404
        mockMvc.perform(get("/api/v1/plans/" + UUID.randomUUID() + "/change-suggestions")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
    }

    // ===================== helpers =====================

    private String link(String token, String planId, String taskId, String targetType, String targetId,
            String linkType, boolean primary) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + taskId + "/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLink(targetType, targetId, linkType, primary)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String jsonLink(String targetType, String targetId, String linkType, boolean primary) {
        return "{\"targetType\":\"" + targetType + "\",\"targetId\":\"" + targetId
                + "\",\"linkType\":\"" + linkType + "\",\"isPrimaryExecution\":" + primary + "}";
    }

    private String createSuggestion(String token, String planId, String taskId, String singleChange)
            throws Exception {
        String body = "{\"title\":\"Task thay đổi\",\"description\":\"Suggestion test\","
                + "\"sourceType\":\"ISSUE\",\"sourceId\":\"" + UUID.randomUUID()
                + "\",\"suggestedChanges\":[" + singleChange + "]}";
        MvcResult res = mockMvc.perform(post("/api/v1/plans/" + planId + "/change-suggestions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        return json(res, "id");
    }

    private String createExecutionTask(String token, String code) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"title\":\"Execution " + code + "\","
                                + "\"type\":\"TASK\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String createIssue(String token, String code) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/issues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"title\":\"Issue " + code + "\","
                                + "\"severity\":\"LOW\",\"ownerId\":\"" + pmUserId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String createRisk(String token, String code) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/risks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"title\":\"Risk " + code + "\","
                                + "\"probability\":\"LOW\",\"impact\":\"LOW\",\"ownerId\":\"" + pmUserId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String createMilestone(String token, String code) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/milestones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"name\":\"MS " + code + "\","
                                + "\"plannedDate\":\"2026-08-10\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
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
        return login("admin.cl", PASSWORD);
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

    // APPEND_MARKER
}
