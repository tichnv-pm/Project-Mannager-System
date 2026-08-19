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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests PLN-BE-07 — resource planning & workload (docs/planning/10, docs/api/13 muc 2.6).
 * Cover PLN-AC-RES-01..07: gán resource (USER/ROLE/EXTERNAL, TEAM bị từ chối), workload theo ngày/wEEK,
 * over-allocation warning, phân quyền MEMBER chỉ thấy workload mình, summary không tính workload, capacity.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanResourceIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PROJECT_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};
    private static final String[] PLAN_MANAGE_PERMS =
            {"plan:view", "plan:create", "plan:update", "plan:delete", "plan:approve",
                    "plan:schedule", "plan:resource"};
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
    private UUID engineerId;
    private UUID memberId;
    private UUID pmId;
    private UUID roleId;

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
        Role engineerRole = createRole("STAFF", "Engineer", new String[0]);

        createUser("admin.res", "admin.res@example.com", adminRole);
        User pm = createUser("pm.res", "pm.res@example.com", pmRole);
        pmId = pm.getId();
        User member = createUser("member.res", "member.res@example.com", memberRole);
        createUser("viewer.res", "viewer.res@example.com", viewerRole);
        User engineer = createUser("engineer.res", "engineer.res@example.com", engineerRole);
        engineerId = engineer.getId();
        memberId = member.getId();
        roleId = engineerRole.getId();

        projectId = createProject(adminToken(), "PRJ-RES", "Project Resource Test");
        addMember(adminToken(), projectId, pm.getId(), ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, member.getId(), ProjectMemberRole.MEMBER);

        createDefaultCalendar(adminToken());
    }

    // ===================== tests =====================

    @Test
    void testRes_Assign_Validate_OverAllocation_Warning() throws Exception {
        String pm = login("pm.res", PASSWORD);
        String planId = createPlan(pm, "PLAN-RES-01", "2026-08-03");

        String t1 = createTask(pm, planId, "T1", "Task 1", "TASK", null, "{\"durationMinutes\":960}");
        String t2 = createTask(pm, planId, "T2", "Task 2", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId); // T1/T2: 03/08..04/08, 480 phút/ngày làm việc

        // PLN-AC-RES-01: gán USER 100%
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + t1 + "/resources")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody("USER", engineerId.toString(), 100, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocationPercent").value(100))
                .andExpect(jsonPath("$.resourceType").value("USER"))
                .andExpect(jsonPath("$.taskSummary").value(false))
                .andExpect(jsonPath("$.overAllocation").value(false));

        // Gán thêm 60% cùng cửa sổ -> 100% + 60% = 160% > 100 % -> warning over-allocation (PLN-AC-RES-03)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + t2 + "/resources")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody("USER", engineerId.toString(), 60, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overAllocation").value(true))
                .andExpect(jsonPath("$.utilizationPercent").value(160.0));

        // PLN-RULE-RES-01: allocation ngoài [1,100] bị từ chối
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + t1 + "/resources")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody("USER", engineerId.toString(), 0, null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + t1 + "/resources")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody("USER", engineerId.toString(), 101, null)))
                .andExpect(status().isBadRequest());

        // PLN-AC-RES-07: type TEAM bị từ chối 400 (không có trong enum)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + t1 + "/resources")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"TEAM\",\"resourceId\":\"" + engineerId + "\"}"))
                .andExpect(status().isBadRequest());

        // duplicate -> 409
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + t1 + "/resources")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody("USER", engineerId.toString(), 100, null)))
                .andExpect(status().isConflict());

        // USER không tồn tại -> 404
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + t1 + "/resources")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody("USER", UUID.randomUUID().toString(), 100, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testRes_Workload_Day_And_Week() throws Exception {
        String pm = login("pm.res", PASSWORD);
        String planId = createPlan(pm, "RES-WL-01", "2026-08-03");
        String t1 = createTask(pm, planId, "T1", "Task 1", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);
        assign(pm, planId, t1, "USER", engineerId.toString(), 100);

        // DAY: 2 bucket, demand 480 phút/ngày = capacity 480 -> utilization 100%, không over
        mockMvc.perform(get("/api/v1/resources/" + engineerId + "/workload")
                        .header("Authorization", "Bearer " + pm)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("DAY"))
                .andExpect(jsonPath("$.totalDemandMinutes").value(960))
                .andExpect(jsonPath("$.totalCapacityMinutes").value(960))
                .andExpect(jsonPath("$.overAllocation").value(false))
                .andExpect(jsonPath("$.buckets.length()").value(2))
                .andExpect(jsonPath("$.buckets[0].date").value("2026-08-03"))
                .andExpect(jsonPath("$.buckets[0].demandMinutes").value(480))
                .andExpect(jsonPath("$.buckets[0].utilizationPercent").value(100.0))
                .andExpect(jsonPath("$.buckets[1].date").value("2026-08-04"))
                .andExpect(jsonPath("$.buckets[1].demandMinutes").value(480));

        // WEEK: 1 bucket tuần 03/08
        mockMvc.perform(get("/api/v1/resources/" + engineerId + "/workload")
                        .header("Authorization", "Bearer " + pm)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04")
                        .param("granularity", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets.length()").value(1))
                .andExpect(jsonPath("$.buckets[0].date").value("2026-08-03"))
                .andExpect(jsonPath("$.buckets[0].demandMinutes").value(960))
                .andExpect(jsonPath("$.buckets[0].utilizationPercent").value(100.0));
    }

    @Test
    void testRes_External_NoCapacity_And_Summary_NoWorkload() throws Exception {
        String pm = login("pm.res", PASSWORD);
        String planId = createPlan(pm, "RES-EXT-01", "2026-08-03");

        // summary + con
        String sum = createTask(pm, planId, "SUM", "Summary", "SUMMARY_TASK", null, null);
        String child = createTask(pm, planId, "C1", "Child 1", "TASK", sum, "{\"durationMinutes\":960}");
        recalc(pm, planId);

        // EXTERNAL gán lên task -> capacity null, không bao giờ over (PLN-AC-RES-06)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + child + "/resources")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"EXTERNAL\",\"resourceId\":\""
                                + UUID.randomUUID() + "\",\"allocationPercent\":100}"))
                .andExpect(status().isCreated());

        // Resource gán lên summary: cho phép nhưng KHÔNG tính workload (PLN-AC-RES-05)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + sum + "/resources")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody("USER", engineerId.toString(), 100, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/resources/" + engineerId + "/workload")
                        .header("Authorization", "Bearer " + pm)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets.length()").value(2))
                .andExpect(jsonPath("$.buckets[0].demandMinutes").value(0))
                .andExpect(jsonPath("$.buckets[0].capacityMinutes").value(480))
                .andExpect(jsonPath("$.buckets[0].overAllocation").value(false));
    }

    @Test
    void testRes_Capacity_Update() throws Exception {
        String pm = login("pm.res", PASSWORD);
        String planId = createPlan(pm, "RES-CAP-01", "2026-08-03");
        String t1 = createTask(pm, planId, "T1", "Task 1", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);
        assign(pm, planId, t1, "USER", engineerId.toString(), 100);

        // capacity 50% từ 03/08
        mockMvc.perform(put("/api/v1/resources/" + engineerId + "/capacity")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"USER\",\"capacityPercent\":50,\"startDate\":\"2026-08-03\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacityPercent").value(50));

        mockMvc.perform(get("/api/v1/resources/" + engineerId + "/workload")
                        .header("Authorization", "Bearer " + pm)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[0].capacityMinutes").value(240))
                .andExpect(jsonPath("$.buckets[0].utilizationPercent").value(200.0))
                .andExpect(jsonPath("$.buckets[0].overAllocation").value(true))
                .andExpect(jsonPath("$.buckets[1].capacityMinutes").value(240))
                .andExpect(jsonPath("$.overAllocation").value(true));
    }

    @Test
    void testRes_Update_Remove() throws Exception {
        String pm = login("pm.res", PASSWORD);
        String planId = createPlan(pm, "RES-RMV-01", "2026-08-03");
        String t1 = createTask(pm, planId, "T1", "Task 1", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);
        String allocId = assign(pm, planId, t1, "USER", engineerId.toString(), 100);

        // PUT sửa allocation 50%
        mockMvc.perform(put("/api/v1/resource-allocations/" + allocId)
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allocationPercent\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationPercent").value(50));

        // DELETE
        mockMvc.perform(delete("/api/v1/resource-allocations/" + allocId)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNoContent());

        // đã gỡ -> workload rỗng
        mockMvc.perform(get("/api/v1/resources/" + engineerId + "/workload")
                        .header("Authorization", "Bearer " + pm)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDemandMinutes").value(0));

        mockMvc.perform(delete("/api/v1/resource-allocations/" + allocId)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
    }

    @Test
    void testRes_CrossPlan_Overview() throws Exception {
        String pm = login("pm.res", PASSWORD);
        String admin = adminToken();
        String plan1 = createPlan(pm, "IND-01", "2026-08-03");
        String project2 = createProject(admin, "PRJ-RES2", "Project Resource 2");
        addMember(admin, project2, pmId, ProjectMemberRole.PROJECT_MANAGER);
        String plan2 = createPlanIn(pm, project2, "IND-02", "2026-08-03");
        String taskA1 = createTask(pm, plan1, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        String taskB1 = createTask(pm, plan1, "B", "Task B", "TASK", null, "{\"durationMinutes\":960}");
        String taskA2 = createTask(pm, plan2, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, plan1);
        recalc(pm, plan2);
        assign(pm, plan1, taskA1, "USER", engineerId.toString(), 100);
        assign(pm, plan1, taskB1, "USER", engineerId.toString(), 60);
        assign(pm, plan2, taskA2, "USER", engineerId.toString(), 100);

        approve(pm, plan1);
        approve(pm, plan2);

        // cross-plan: plan1 (A 100% + B 60%) + plan2 (A 100%) = 1536 + 960 = 2496 phút vs capacity 960 
        mockMvc.perform(get("/api/v1/resources/overview")
                        .header("Authorization", "Bearer " + pm)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].resourceType").value("USER"))
                .andExpect(jsonPath("$[0].overAllocation").value(true))
                .andExpect(jsonPath("$[0].demandMinutes").value(2496))
                .andExpect(jsonPath("$[0].capacityMinutes").value(960));
    }

    @Test
    void testRes_PlanWorkload_Eac_Member() throws Exception {
        String pm = login("pm.res", PASSWORD);
        String member = login("member.res", PASSWORD);
        String planId = createPlan(pm, "RES-ACC-01", "2026-08-03");
        createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);

        // member còn bị gán vào task
        String taskId = getTaskId(pm, planId, "A");
        assign(pm, planId, taskId, "USER", memberId.toString(), 100);

        // plan workload với admin: 1 mục; member: LUÔN thấy chính mình (1 mục) — đơn giản với 1 resource duy nhất
        mockMvc.perform(get("/api/v1/plans/" + planId + "/workload")
                        .header("Authorization", "Bearer " + pm)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].resourceId").value(memberId.toString()));

        mockMvc.perform(get("/api/v1/plans/" + planId + "/workload")
                        .header("Authorization", "Bearer " + member)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].resourceId").value(memberId.toString()));

        // RES-04: member không xem workload người khác (403), xem chính mình được
        mockMvc.perform(get("/api/v1/resources/" + engineerId + "/workload")
                        .header("Authorization", "Bearer " + member)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/resources/" + memberId + "/workload")
                        .header("Authorization", "Bearer " + member)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04"))
                .andExpect(status().isOk());

        // viewer không có plan:view -> 403
        String viewer = login("viewer.res", PASSWORD);
        mockMvc.perform(get("/api/v1/resources/overview")
                        .header("Authorization", "Bearer " + viewer)
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-04"))
                .andExpect(status().isForbidden());

        // member chặn gán resource (thiếu plan:resource)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + taskId + "/resources")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody("USER", engineerId.toString(), 100, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRes_ListPlanResources() throws Exception {
        String pm = login("pm.res", PASSWORD);
        String member = login("member.res", PASSWORD);
        String planId = createPlan(pm, "RES-LIST-01", "2026-08-03");
        String taskA = createTask(pm, planId, "A", "Task A", "TASK", null, "{\"durationMinutes\":960}");
        String taskB = createTask(pm, planId, "B", "Task B", "TASK", null, "{\"durationMinutes\":960}");
        recalc(pm, planId);
        assign(pm, planId, taskA, "USER", engineerId.toString(), 100);
        assign(pm, planId, taskB, "ROLE", roleId.toString(), 50);

        // GET list resources của plan -> 200, đủ task/resource/percent
        mockMvc.perform(get("/api/v1/plans/" + planId + "/resources")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].taskCode").value("A"))
                .andExpect(jsonPath("$[0].resourceType").value("USER"))
                .andExpect(jsonPath("$[0].resourceName").isNotEmpty())
                .andExpect(jsonPath("$[0].allocationPercent").value(100))
                .andExpect(jsonPath("$[1].taskCode").value("B"))
                .andExpect(jsonPath("$[1].resourceType").value("ROLE"))
                .andExpect(jsonPath("$[1].allocationPercent").value(50));

        // member (plan:view) đọc được danh sách
        mockMvc.perform(get("/api/v1/plans/" + planId + "/resources")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // plan không tồn tại -> 404
        mockMvc.perform(get("/api/v1/plans/" + UUID.randomUUID() + "/resources")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNotFound());
    }

    // ===================== helpers =====================

    private String assign(String token, String planId, String taskId, String type, String resourceId, int percent)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks/" + taskId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(type, resourceId, percent, null)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String assignBody(String resourceType, String resourceId, Integer percent, String startDate) {
        String pct = percent == null ? "" : ",\"allocationPercent\":" + percent;
        String start = startDate == null ? "" : ",\"startDate\":\"" + startDate + "\"";
        return "{\"resourceType\":\"" + resourceType + "\",\"resourceId\":\"" + resourceId + "\"" + pct + start + "}";
    }

    private void recalc(String token, String planId) throws Exception {
        mockMvc.perform(post("/api/v1/plans/" + planId + "/recalc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void approve(String token, String planId) throws Exception {
        mockMvc.perform(post("/api/v1/plans/" + planId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/plans/" + planId + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String getTaskId(String token, String planId, String code) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = objectMapper.readTree(res.getResponse().getContentAsString());
        for (JsonNode node : tree) {
            if (code.equals(node.get("taskCode").asText())) {
                return node.get("id").asText();
            }
        }
        throw new IllegalStateException("Task " + code + " không tìm thấy");
    }

    private String createPlan(String token, String code, String start) throws Exception {
        return createPlanIn(token, projectId, code, start);
    }

    private String createPlanIn(String token, String prjId, String code, String start) throws Exception {
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
                                """.formatted(prjId, code, code, startJson)))
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
        return login("admin.res", PASSWORD);
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