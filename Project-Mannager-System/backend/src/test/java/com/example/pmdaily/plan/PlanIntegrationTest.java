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
 * Integration tests PLN-BE-01 — Project Plan module (docs/api/13-planning-api.md muc 2.1).
 * Cover: CRUD, planCode unique, master-detail rules, vòng đời submit/approve/activate,
 * 1 Master ACTIVE/dự án, xóa master còn detail con bị từ chối, phân quyền.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PROJECT_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};
    private static final String[] PLAN_ADMIN_PERMS =
            {"plan:view", "plan:create", "plan:update", "plan:delete", "plan:approve"};
    private static final String[] MEMBER_PERMS = {"plan:view", "plan:create"};

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

        Role adminRole = createRole("ADMIN", "Admin", concat(PROJECT_PERMS, PLAN_ADMIN_PERMS));
        Role pmRole = createRole("PROJECT_MANAGER", "PM", concat(PROJECT_PERMS, PLAN_ADMIN_PERMS));
        Role memberRole = createRole("PROJECT_MEMBER", "Member", MEMBER_PERMS);

        User admin = createUser("admin.plan", "admin.plan@example.com", adminRole);
        User pm = createUser("pm.plan", "pm.plan@example.com", pmRole);
        User member = createUser("member.plan", "member.plan@example.com", memberRole);

        pmUserId = pm.getId();
        memberUserId = member.getId();

        projectId = createProject(adminToken(), "PRJ-PLAN", "Project Planning Test");
        addMember(adminToken(), projectId, pmUserId, ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, memberUserId, ProjectMemberRole.DEVELOPER);
    }

    @Test
    void testPlanLifecycle_CreateMasterDetail_SubmitApproveActivate() throws Exception {
        String pmToken = login("pm.plan", PASSWORD);

        // 1. Create Master Plan -> 201, DRAFT, version 1 tự tạo
        MvcResult masterRes = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "PLAN-MASTER-01", "Master Toàn trình", "MASTER", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.planType").value("MASTER"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.progress").value(0))
                .andExpect(jsonPath("$.activeVersionNo").value(1))
                .andExpect(jsonPath("$.parentPlanId").doesNotExist())
                .andReturn();
        String masterId = json(masterRes, "id");

        // 2. Duplicate planCode -> 409
        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "PLAN-MASTER-01", "Trùng mã", "MASTER", null)))
                .andExpect(status().isConflict());

        // 3. Invalid transition: approve DRAFT -> 400
        mockMvc.perform(post("/api/v1/plans/" + masterId + "/approve")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        // 4. Create Detail Plan -> 201 (cha là Master)
        MvcResult detailRes = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "PLAN-DET-01", "Detail Backend", "DETAIL", masterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planType").value("DETAIL"))
                .andExpect(jsonPath("$.parentPlanId").value(masterId))
                .andReturn();
        String detailId = json(detailRes, "id");
        String detailVersion = json(detailRes, "version");

        // 5. Detail Plan thiếu parent -> 400 INVALID_PARENT_PLAN
        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "PLAN-DET-02", "Detail thiếu cha", "DETAIL", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARENT_PLAN"));

        // 6. Detail Plan cha là Detail -> 400 INVALID_PARENT_PLAN
        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "PLAN-DET-03", "Detail lồng detail", "DETAIL", detailId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARENT_PLAN"));

        // 7. Master Plan không có parentPlanId; tạo MASTER có parent -> 400
        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "PLAN-MASTER-02", "Master sai cấu trúc", "MASTER", masterId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARENT_PLAN"));

        // 8. List theo filter projectId + planType -> 2 bản
        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .param("projectId", projectId)
                        .param("planType", "MASTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));

        // 9. Vòng đời: submit -> approve -> activate (submit yêu cầu ≥ 1 planning task)
        mockMvc.perform(post("/api/v1/plans/" + masterId + "/tasks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskCode": "T-1",
                                  "taskName": "Task 1",
                                  "taskType": "TASK"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/plans/" + masterId + "/submit")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/v1/plans/" + masterId + "/approve")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/plans/" + masterId + "/activate")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 10. Master thứ 2 không approve được khi đã có 1 Master APPROVED/ACTIVE -> 409
        MvcResult master2Res = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "PLAN-MASTER-03", "Master thứ hai", "MASTER", null)))
                .andExpect(status().isCreated())
                .andReturn();
        String master2Id = json(master2Res, "id");
        mockMvc.perform(post("/api/v1/plans/" + master2Id + "/tasks")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskCode": "T-2",
                                  "taskName": "Task 2",
                                  "taskType": "TASK"
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/plans/" + master2Id + "/submit")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/plans/" + master2Id + "/approve")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isConflict());

        // 11. Update Detail Plan (optimistic locking) -> 200; version cũ -> 409
        mockMvc.perform(put("/api/v1/plans/" + detailId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planName": "Detail Backend - v2",
                                  "plannedStart": "2026-08-10",
                                  "plannedFinish": "2026-09-30",
                                  "version": %s
                                }
                                """.formatted(detailVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value("Detail Backend - v2"))
                .andExpect(jsonPath("$.plannedStart").value("2026-08-10"))
                .andReturn();

        mockMvc.perform(put("/api/v1/plans/" + detailId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planName": "Detail cũ",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isConflict());

        // 12. Xóa Master còn Detail con -> 400 HAS_CHILDREN
        mockMvc.perform(delete("/api/v1/plans/" + masterId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HAS_CHILDREN"));

        // 13. Xóa Detail -> 204; xóa Master -> 204; GET -> 404
        mockMvc.perform(delete("/api/v1/plans/" + detailId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/plans/" + detailId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/plans/" + masterId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        // 14. Date range sai -> 400 INVALID_DATE_RANGE
        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "PLAN-MASTER-04", "Sai ngày",
                                "MASTER", null, "2026-12-31", "2026-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void testPlanAccessControl_MemberViewOnly() throws Exception {
        String pmToken = login("pm.plan", PASSWORD);
        String memberToken = login("member.plan", PASSWORD);

        MvcResult masterRes = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "PLAN-MASTER-ACC", "Master Access", "MASTER", null)))
                .andExpect(status().isCreated())
                .andReturn();
        String masterId = json(masterRes, "id");

        // Member (DEV, không phải PM dự án) dù có quyền plan:create vẫn bị từ chối ở service -> 403
        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanJson(projectId, "PLAN-MASTER-ACC2", "Member tạo", "MASTER", null)))
                .andExpect(status().isForbidden());

        // Member thuộc dự án được xem danh sách + chi tiết
        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer " + memberToken)
                        .param("projectId", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(get("/api/v1/plans/" + masterId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(masterId));

        // Người không phải thành viên dự án không xem được -> 403
        User stranger = createUser("stranger.plan", "stranger.plan@example.com",
                createRole("STRANGER", "Stranger", new String[]{"plan:view"}));
        String strangerToken = login("stranger.plan", PASSWORD);
        mockMvc.perform(get("/api/v1/plans/" + masterId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    private String createPlanJson(String prjId, String code, String name, String type, String parentId) {
        return createPlanJson(prjId, code, name, type, parentId, null, null);
    }

    private String createPlanJson(String prjId, String code, String name, String type, String parentId,
            String start, String finish) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"projectId\":\"").append(prjId)
                .append("\",\"planCode\":\"").append(code)
                .append("\",\"planName\":\"").append(name)
                .append("\",\"planType\":\"").append(type);
        if (parentId != null) {
            sb.append("\",\"parentPlanId\":\"").append(parentId);
        }
        if (start != null) {
            sb.append("\",\"plannedStart\":\"").append(start);
        }
        if (finish != null) {
            sb.append("\",\"plannedFinish\":\"").append(finish);
        }
        sb.append("\"}");
        return sb.toString();
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
        return login("admin.plan", PASSWORD);
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
