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
 * Integration tests PLN-BE-04 — plan-calendar (docs/api/13-planning-api.md muc 2.4).
 * Cover PLN-AC-CAL-01..05: tạo calendar + working days, validation, update & optimistic lock,
 * exception (holiday WORKING/NON_WORKING, unique date), effective fallback org cho plan,
 * xóa bị chặn khi còn tham chiếu, phân quyền (chỉ ADMIN tổ chức quản lý).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanCalendarIntegrationTest {

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

        createUser("admin.cal", "admin.cal@example.com", adminRole);
        User pm = createUser("pm.cal", "pm.cal@example.com", pmRole);
        User member = createUser("member.cal", "member.cal@example.com", memberRole);

        projectId = createProject(adminToken(), "PRJ-CAL", "Project Calendar Test");
        addMember(adminToken(), projectId, pm.getId(), ProjectMemberRole.PROJECT_MANAGER);
        addMember(adminToken(), projectId, member.getId(), ProjectMemberRole.MEMBER);
    }

    // ===================== tests =====================

    @Test
    void testCal_Create_WithWorkingDays_AndDefaultHours() throws Exception {
        String admin = adminToken();

        // Tạo calendar với 5 ngày làm việc + 6/7 nghỉ (PLN-AC-CAL-01)
        MvcResult res = mockMvc.perform(post("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(standardWorkingDays("Calendar Org", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Calendar Org"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.dailyWorkingHours").value(8))
                .andExpect(jsonPath("$.workingDays.length()").value(7))
                .andExpect(jsonPath("$.workingDays[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.workingDays[0].isWorking").value(true))
                .andExpect(jsonPath("$.workingDays[5].isWorking").value(false))
                .andExpect(jsonPath("$.exceptions.length()").value(0))
                .andReturn();
        String calendarId = json(res, "id");
        org.junit.jupiter.api.Assertions.assertNotNull(calendarId);

        // List trả về calendar vừa tạo (plan:view)
        mockMvc.perform(get("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(calendarId));
    }

    @Test
    void testCal_Create_DuplicateDayAndInvalidValuesRejected() throws Exception {
        String admin = adminToken();

        // dayOfWeek ngoài 1-7 -> 400 VALIDATION_ERROR
        mockMvc.perform(post("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cal Bad",
                                  "workingDays": [ {"dayOfWeek": 8, "isWorking": true} ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // dayOfWeek bị lặp -> 400 (service-level rule)
        mockMvc.perform(post("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cal Dup",
                                  "workingDays": [
                                    {"dayOfWeek": 1, "isWorking": true},
                                    {"dayOfWeek": 1, "isWorking": false}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // name bắt buộc -> 400 VALIDATION_ERROR
        mockMvc.perform(post("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "workingDays": [] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void testCal_Update_ReplacesWorkingDays_OptimisticLock() throws Exception {
        String admin = adminToken();
        String calendarId = createCalendar(admin, "Cal Update", null);

        long version = getCalendarVersion(admin, calendarId);

        // Update: đổi giờ làm việc + chỉ còn thứ 2->thứ 6 (PLN-AC-CAL-02, workingDays thay thế toàn bộ)
        mockMvc.perform(put("/api/v1/plan-calendars/" + calendarId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "name": "Cal Updated",
                                  "dailyWorkingHours": 9,
                                  "timezone": "Asia/Ho_Chi_Minh",
                                  "status": "INACTIVE",
                                  "workingDays": [
                                    {"dayOfWeek": 1, "isWorking": true, "startTime": "08:30", "endTime": "17:30"},
                                    {"dayOfWeek": 2, "isWorking": true, "startTime": "08:30", "endTime": "17:30"},
                                    {"dayOfWeek": 3, "isWorking": true, "startTime": "08:30", "endTime": "17:30"},
                                    {"dayOfWeek": 4, "isWorking": true, "startTime": "08:30", "endTime": "17:30"},
                                    {"dayOfWeek": 5, "isWorking": true, "startTime": "08:30", "endTime": "17:30"},
                                    {"dayOfWeek": 6, "isWorking": true, "startTime": "08:30", "endTime": "17:30"}
                                  ]
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cal Updated"))
                .andExpect(jsonPath("$.dailyWorkingHours").value(9))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.workingDays.length()").value(6))
                .andExpect(jsonPath("$.workingDays[5].dayOfWeek").value(6))
                .andExpect(jsonPath("$.workingDays[5].isWorking").value(true));

        // version cũ -> 409 optimistic lock
        mockMvc.perform(put("/api/v1/plan-calendars/" + calendarId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "name": "Cal Stale",
                                  "status": "ACTIVE"
                                }
                                """.formatted(version)))
                .andExpect(status().isConflict());
    }

    @Test
    void testCal_Exceptions_Add_UniqueDate() throws Exception {
        String admin = adminToken();
        String calendarId = createCalendar(admin, "Cal Exc", null);

        // Thêm ngày lễ (PLN-AC-CAL-04 đầu: exception NON_WORKING)
        mockMvc.perform(post("/api/v1/plan-calendars/" + calendarId + "/exceptions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "exceptionDate": "2026-09-02",
                                  "exceptionType": "NON_WORKING",
                                  "note": "Quốc khánh"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exceptionDate").value("2026-09-02"))
                .andExpect(jsonPath("$.exceptionType").value("NON_WORKING"));

        // duplicate (calendar_id, exception_date) -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/plan-calendars/" + calendarId + "/exceptions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "exceptionDate": "2026-09-02",
                                  "exceptionType": "WORKING"
                                }
                                """))
                .andExpect(status().isConflict());

        // Ngày làm bù weekend (PLN-AC-CAL-05): exception WORKING
        mockMvc.perform(post("/api/v1/plan-calendars/" + calendarId + "/exceptions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "exceptionDate": "2026-09-05",
                                  "exceptionType": "WORKING",
                                  "note": "Làm bù thứ 7"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exceptionType").value("WORKING"))
                .andExpect(jsonPath("$.note").value("Làm bù thứ 7"));

        // List calendar phản ánh exception
        mockMvc.perform(get("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exceptions.length()").value(2));
    }

    @Test
    void testCal_Effective_Plan_Fallback_AndMerge() throws Exception {
        String admin = adminToken();
        String pm = login("pm.cal", PASSWORD);

        // Calendar org mặc định (Mon-Fri, default hours 8)
        String orgCalendarId = createCalendar(admin, "Org Standard", null);

        // Plan không gắn calendarId -> fallback về calendar mặc định (PLN-AC-CAL-03)
        String planId = createPlan(pm, "PLAN-CAL-01", "Master Fallback", null);
        mockMvc.perform(get("/api/v1/plans/" + planId + "/calendar")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Org Standard"))
                .andExpect(jsonPath("$.workingDays.length()").value(7))
                .andExpect(jsonPath("$.workingDays[0].isWorking").value(true))
                .andExpect(jsonPath("$.workingDays[6].isWorking").value(false));

        // Calendar project kế thừa cha (parent=org) + exception WORKING cuối tuần (PLN-AC-CAL-05)
        String excId = createCalendar(admin, "Cal Project", orgCalendarId);
        mockMvc.perform(post("/api/v1/plan-calendars/" + excId + "/exceptions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "exceptionDate": "2026-09-06",
                                  "exceptionType": "WORKING",
                                  "note": "Làm bù chủ nhật"
                                }
                                """))
                .andExpect(status().isCreated());

        String plan2Id = createPlan(pm, "PLAN-CAL-02", "Master With Calendar", excId);
        mockMvc.perform(get("/api/v1/plans/" + plan2Id + "/calendar")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(excId))
                .andExpect(jsonPath("$.exceptions[0].exceptionDate").value("2026-09-06"))
                .andExpect(jsonPath("$.exceptions[0].exceptionType").value("WORKING"));
    }

    @Test
    void testCal_Delete_ReferencedOrParent_Rejected() throws Exception {
        String admin = adminToken();
        String pm = login("pm.cal", PASSWORD);

        String orgCalendarId = createCalendar(admin, "Cal Org", null);
        String usedCalendarId = createCalendar(admin, "Cal Dung", null);
        createPlan(pm, "PLAN-CAL-03", "Master In Use", usedCalendarId);

        // Calendar đang được plan tham chiếu -> 400 HAS_CHILDREN
        mockMvc.perform(delete("/api/v1/plan-calendars/" + usedCalendarId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HAS_CHILDREN"));

        // Calendar còn calendar con -> 400 HAS_CHILDREN
        String child = createCalendar(admin, "Cal Child", orgCalendarId);
        mockMvc.perform(delete("/api/v1/plan-calendars/" + orgCalendarId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HAS_CHILDREN"));

        // Xóa thành công khi không tham chiếu -> 204, sau đó 404
        mockMvc.perform(delete("/api/v1/plan-calendars/" + child)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/plan-calendars/" + child)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCal_AccessControl_AdminOnly_And_ViewAccess() throws Exception {
        String admin = adminToken();
        String pm = login("pm.cal", PASSWORD);
        String member = login("member.cal", PASSWORD);

        // PM có plan:* nhưng không ADMIN org -> 403 khi tạo (chưa quyền plan:update -> 403 method security)
        mockMvc.perform(post("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cal PM",
                                  "workingDays": []
                                }
                                """))
                .andExpect(status().isForbidden());

        String adminCalendarId = createCalendar(admin, "Cal Admin", null);

        // PM xem danh sách OK (plan:view)
        mockMvc.perform(get("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());

        // PM sửa / xóa calendar -> 403 (service orgAdmin check)
        mockMvc.perform(put("/api/v1/plan-calendars/" + adminCalendarId)
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0,
                                  "name": "Cal Hack",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/plan-calendars/" + adminCalendarId)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isForbidden());

        // PM là thành viên dự án được xem effective calendar của plan (plan:view + trong project)
        String planId = createPlan(pm, "PLAN-CAL-04", "Master Member", adminCalendarId);
        mockMvc.perform(get("/api/v1/plans/" + planId + "/calendar")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk());

        // Member dự án cũng xem được (plan:view + thành viên project)
        mockMvc.perform(get("/api/v1/plans/" + planId + "/calendar")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());

        // User không có quyền plan -> 403
        Role viewerRole = createRole("VIEWER_CAL", "Viewer", new String[] {});
        createUser("viewer.cal", "viewer.cal@example.com", viewerRole);
        String viewer = login("viewer.cal", PASSWORD);
        mockMvc.perform(get("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden());
    }

    // ===================== helpers =====================

    private String createCalendar(String token, String name, String parentId) throws Exception {
        String parent = parentId == null ? "" : ",\"parentCalendarId\":\"" + parentId + "\"";
        MvcResult res = mockMvc.perform(post("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"%s
                                }
                                """.formatted(name, parent)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(res, "id");
    }

    /** JSON body tạo calendar chuẩn: Mon-Fri 08:00-17:00, Sat/Sun nghỉ. */
    private String standardWorkingDays(String name, String parentId) {
        String parent = parentId == null ? "" : "\"parentCalendarId\":\"" + parentId + "\",";
        return """
                {
                  "name": "%s",
                  %s
                  "dailyWorkingHours": 8,
                  "timezone": "Asia/Ho_Chi_Minh",
                  "workingDays": [
                    {"dayOfWeek": 1, "isWorking": true, "startTime": "08:00", "endTime": "17:00"},
                    {"dayOfWeek": 2, "isWorking": true, "startTime": "08:00", "endTime": "17:00"},
                    {"dayOfWeek": 3, "isWorking": true, "startTime": "08:00", "endTime": "17:00"},
                    {"dayOfWeek": 4, "isWorking": true, "startTime": "08:00", "endTime": "17:00"},
                    {"dayOfWeek": 5, "isWorking": true, "startTime": "08:00", "endTime": "17:00"},
                    {"dayOfWeek": 6, "isWorking": false, "startTime": null, "endTime": null},
                    {"dayOfWeek": 7, "isWorking": false, "startTime": null, "endTime": null}
                  ]
                }
                """.formatted(name, parent);
    }

    private long getCalendarVersion(String token, String calendarId) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/plan-calendars")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        for (JsonNode c : node) {
            if (c.get("id").asText().equals(calendarId)) {
                return c.get("version").asLong();
            }
        }
        throw new IllegalStateException("calendar " + calendarId + " not in list");
    }

    private String createPlan(String token, String code, String name, String calendarId) throws Exception {
        String cal = calendarId == null ? "" : ",\"calendarId\":\"" + calendarId + "\"";
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
                                """.formatted(projectId, code, name, cal)))
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
        return login("admin.cal", PASSWORD);
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