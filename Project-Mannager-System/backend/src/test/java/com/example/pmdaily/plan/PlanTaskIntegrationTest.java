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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests PLN-BE-02 — WBS / Plan Task module (docs/api/13-planning-api.md muc 2.2).
 * Cover PLN-AC-WBS-01..09: wbsCode tự đánh, chặn vòng lặp cha-con, task lá làm cha,
 * xóa summary còn con (HAS_CHILDREN), milestone rules, roll-up tiến độ theo effort/duration/avg,
 * renumber khi xóa con, move up/down/indent/outdent/to-parent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanTaskIntegrationTest {

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

        createUser("admin.wbs", "admin.wbs@example.com", adminRole);
        User pm = createUser("pm.wbs", "pm.wbs@example.com", pmRole);
        User member = createUser("member.wbs", "member.wbs@example.com", memberRole);

        pmUserId = pm.getId();
        memberUserId = member.getId();

        projectId = createProject(adminToken(), "PRJ-WBS", "Project WBS Test");
        addMember(adminToken(), projectId, pmUserId, ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, memberUserId, ProjectMemberRole.DEVELOPER);
    }

    @Test
    void testWbs_CreateTree_Renumber_Rollup_Delete() throws Exception {
        String pmToken = login("pm.wbs", PASSWORD);

        // Plan chưa có task -> submit bị từ chối (PLN-AC-PLAN-05: cần >= 1 task)
        MvcResult planRes = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(projectId, "PLAN-WBS-01", "Master WBS", "MASTER")))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = json(planRes, "id");

        mockMvc.perform(post("/api/v1/plans/" + planId + "/submit")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 1. Tạo 2 root: PHASE -> wbs "1", WORK_PACKAGE -> wbs "2" (PLN-AC-WBS-01)
        String phaseId = createTask(pmToken, planId, "PHASE-1", "Phase 1 - Khởi tạo", "PHASE", null,
                null, null, null, null);
        String wpId = createTask(pmToken, planId, "WP-1", "Work package 1", "WORK_PACKAGE", null,
                null, null, null, null);

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].wbsCode").value("1"))
                .andExpect(jsonPath("$[0].isSummary").value(false))
                .andExpect(jsonPath("$[1].wbsCode").value("2"));

        // 2. Task con -> "1.1", "1.2" (outlineLevel 2); childA là WORK_PACKAGE để sinh cháu
        String childA = createTask(pmToken, planId, "T-1.1", "Work package 1", "WORK_PACKAGE", phaseId,
                null, null, null, null);
        String childB = createTask(pmToken, planId, "T-1.2", "Task B", "TASK", phaseId,
                null, null, null, null);

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].wbsCode").value("1.1"))
                .andExpect(jsonPath("$[1].outlineLevel").value(2))
                .andExpect(jsonPath("$[2].wbsCode").value("1.2"));

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isSummary").value(true));

        // 3. Cháu -> "1.1.1"
        String grandchildId = createTask(pmToken, planId, "T-1.1.1", "Grandchild", "TASK", childA,
                null, null, null, null);

        // 4. Task lá (TASK) làm cha -> 400 INVALID_PARENT (PLN-AC-WBS-03)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("T-INVALID", "Invalid child", "TASK", childB, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARENT"));

        // 5. taskCode trùng -> 409
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("T-1.1", "Duplicate", "TASK", phaseId, null)))
                .andExpect(status().isConflict());

        // 6. Milestone: duration 0, isMilestone, start == finish, percent 0/100 (PLN-AC-WBS-05)
        MvcResult msRes = mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskCode": "MS-1",
                                  "taskName": "Go-live",
                                  "taskType": "MILESTONE",
                                  "parentId": "%s",
                                  "plannedStart": "2026-10-01",
                                  "percentComplete": 100
                                }
                                """.formatted(phaseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isMilestone").value(true))
                .andExpect(jsonPath("$.durationMinutes").value(0))
                .andExpect(jsonPath("$.plannedFinish").value("2026-10-01"))
                .andExpect(jsonPath("$.percentComplete").value(100))
                .andExpect(jsonPath("$.wbsCode").value("1.3"))
                .andReturn();
        String msId = json(msRes, "id");

        // Milestone percentComplete không hợp lệ -> 400
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskCode": "MS-2",
                                  "taskName": "Bad milestone",
                                  "taskType": "MILESTONE",
                                  "parentId": "%s",
                                  "percentComplete": 50
                                }
                                """.formatted(phaseId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 7. Xóa summary còn con -> 400 HAS_CHILDREN (PLN-AC-WBS-04)
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + phaseId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HAS_CHILDREN"));

        // Xóa milestone MS-1 (leaf) để roll-up phase không bị nhiễu ngày 2026-10-01
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + msId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        // 8. Roll-up: set effort + progress cho leaf (PLN-AC-WBS-06, 07)
        String childAVersion = getTask(pmToken, planId, grandchildId, "version");
        updateTask(pmToken, planId, grandchildId, childAVersion,
                "Grandchild updated", 40, 100, "2026-08-01", "2026-08-10");
        String childBVersion = getTask(pmToken, planId, childB, "version");
        updateTask(pmToken, planId, childB, childBVersion,
                "Task B", 60, 50, "2026-08-05", "2026-08-20");

        // Summary Phase: progress = (40*100 + 60*50)/100 = 70; start 2026-08-01, finish 2026-08-20
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].percentComplete").value(70))
                .andExpect(jsonPath("$[0].plannedStart").value("2026-08-01"))
                .andExpect(jsonPath("$[0].plannedFinish").value("2026-08-20"))
                .andExpect(jsonPath("$[0].plannedEffortMinutes").value(100));

        // Plan roll-up: progress 70, start 2026-08-01, finish 2026-08-20
        mockMvc.perform(get("/api/v1/plans/" + planId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(70))
                .andExpect(jsonPath("$.plannedStart").value("2026-08-01"))
                .andExpect(jsonPath("$.plannedFinish").value("2026-08-20"));

        // 9. Summary percentComplete không sửa tay -> 400 (PLN-RULE-WBS-08)
        String phaseVersion = getTask(pmToken, planId, phaseId, "version");
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + phaseId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskName": "Phase 1",
                                  "percentComplete": 100,
                                  "version": %s
                                }
                                """.formatted(phaseVersion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 10. Update version cũ -> 409
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + childA)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskName": "Task A stale",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isConflict());

        // 11. Xóa con -> renumber sibling còn lại (PLN-AC-WBS-09)
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + childB)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].wbsCode").value("1.1"));

        // 12. Xóa hết con -> xóa summary thành công
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + grandchildId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + childA)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + wpId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/plans/" + planId + "/tasks/" + phaseId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        // 13. Roll-up rỗng -> plan progress 0
        mockMvc.perform(get("/api/v1/plans/" + planId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(0));
    }

    @Test
    void testWbs_MoveRules_CircularParent_IndentOutdent() throws Exception {
        String pmToken = login("pm.wbs", PASSWORD);

        MvcResult planRes = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(projectId, "PLAN-WBS-02", "Master Move", "MASTER")))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = json(planRes, "id");

        String rootA = createTask(pmToken, planId, "R-A", "Root A", "PHASE", null, null, null, null, null);
        String rootB = createTask(pmToken, planId, "R-B", "Root B", "PHASE", null, null, null, null, null);
        String childOfA = createTask(pmToken, planId, "C-A", "Child A1", "TASK", rootA, null, null, null, null);

        // Move UP: rootB lên trước rootA -> wbs renumber "1"/"2"
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + rootB + "/move")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"UP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wbsCode").value("1"));

        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskCode").value("R-B"))
                .andExpect(jsonPath("$[1].taskCode").value("R-A"))
                .andExpect(jsonPath("$[1].wbsCode").value("2"))
                .andExpect(jsonPath("$[2].wbsCode").value("2.1"));

        // INDENT: rootA thành con của rootB (sibling trước)
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + rootA + "/move")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"INDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wbsCode").value("1.1"));

        // OUTDENT: rootA lên cấp cao nhất (cha của cha = null)
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + rootA + "/move")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"OUTDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wbsCode").value("2"));

        // TO_PARENT: vòng lặp — childOfA làm cha của rootA (hậu duệ của chính nó) -> CIRCULAR_PARENT
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + rootA + "/move")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction": "TO_PARENT",
                                  "targetParentId": "%s"
                                }
                                """.formatted(childOfA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CIRCULAR_PARENT"));

        // TO_PARENT: parent là task lá -> INVALID_PARENT
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + rootB + "/move")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction": "TO_PARENT",
                                  "targetParentId": "%s"
                                }
                                """.formatted(childOfA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARENT"));

        // Move UP khi đã ở đầu danh sách (rootB) -> 400
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + rootB + "/move")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"UP\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testWbs_AccessControl() throws Exception {
        String pmToken = login("pm.wbs", PASSWORD);
        String memberToken = login("member.wbs", PASSWORD);

        MvcResult planRes = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(projectId, "PLAN-WBS-03", "Master Access", "MASTER")))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = json(planRes, "id");
        createTask(pmToken, planId, "R-X", "Root X", "PHASE", null, null, null, null, null);

        // Member (chỉ plan:view) xem tree OK
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Member tạo task -> 403 (thiếu quyền plan:update)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("M-TASK", "Member task", "TASK", null, null)))
                .andExpect(status().isForbidden());

        // Người không phải thành viên dự án -> 403 (service check)
        createUser("stranger.wbs", "stranger.wbs@example.com",
                createRole("STRANGER", "Stranger", concat(new String[]{}, new String[]{"plan:view"})));
        String strangerToken = login("stranger.wbs", PASSWORD);
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // ===================== helpers =====================

    private String createTask(String token, String planId, String code, String name, String type,
            String parentId, Integer effort, Integer percent, String start, String finish) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"taskCode\":\"").append(code)
                .append("\",\"taskName\":\"").append(name)
                .append("\",\"taskType\":\"").append(type);
        if (parentId != null) {
            sb.append("\",\"parentId\":\"").append(parentId);
        }
        if (effort != null) {
            sb.append("\",\"plannedEffortMinutes\":").append(effort);
        }
        if (percent != null) {
            sb.append("\",\"percentComplete\":").append(percent);
        }
        if (start != null) {
            sb.append("\",\"plannedStart\":\"").append(start);
        }
        if (finish != null) {
            sb.append("\",\"plannedFinish\":\"").append(finish);
        }
        sb.append("\"}");
        MvcResult res = mockMvc.perform(post("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sb.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    private String taskJson(String code, String name, String type, String parentId, Integer percent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"taskCode\":\"").append(code)
                .append("\",\"taskName\":\"").append(name)
                .append("\",\"taskType\":\"").append(type);
        if (parentId != null) {
            sb.append("\",\"parentId\":\"").append(parentId);
        }
        if (percent != null) {
            sb.append("\",\"percentComplete\":").append(percent);
        }
        sb.append("\"}");
        return sb.toString();
    }

    private void updateTask(String token, String planId, String taskId, String version,
            String name, Integer effort, Integer percent, String start, String finish) throws Exception {
        String body = """
                {
                  "taskName": "%s",
                  "plannedEffortMinutes": %d,
                  "percentComplete": %d,
                  "plannedStart": "%s",
                  "plannedFinish": "%s",
                  "version": %s
                }
                """.formatted(name, effort, percent, start, finish, version);
        mockMvc.perform(put("/api/v1/plans/" + planId + "/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String getTask(String token, String planId, String taskId, String field) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(res.getResponse().getContentAsString())) {
            if (node.get("id").asText().equals(taskId)) {
                return node.get(field).asText();
            }
        }
        throw new IllegalStateException("task not found " + taskId);
    }

    private String planJson(String prjId, String code, String name, String type) {
        return """
                {
                  "projectId": "%s",
                  "planCode": "%s",
                  "planName": "%s",
                  "planType": "%s"
                }
                """.formatted(prjId, code, name, type);
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
        return login("admin.wbs", PASSWORD);
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

    private String[] concat(String[] a, String[] b) {
        String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
