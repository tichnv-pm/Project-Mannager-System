package com.example.pmdaily.actionitem;

import java.time.LocalDate;
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
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.project.ProjectMemberRepository;
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
 * Integration test Action Item module (docs/api/07-action-item-api.md, FR-AI-01..04, BR-AI-01..04).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActionItemIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] ADMIN_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage",
                    "meeting:view", "meeting:manage", "action-item:view", "action-item:manage"};
    private static final String[] PM_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage",
                    "meeting:view", "meeting:manage", "action-item:view", "action-item:manage"};
    private static final String[] MEMBER_PERMS =
            {"meeting:view", "action-item:view"};

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
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository memberRepository;
    @Autowired
    private ActionItemRepository actionItemRepository;

    private UUID adminId;
    private UUID pmUserId;
    private UUID assigneeId;
    private UUID outsiderId;
    private String projectId;
    private String meetingId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                statement.executeUpdate("DELETE FROM meeting_participants");
                statement.executeUpdate("DELETE FROM attachments");
                statement.executeUpdate("DELETE FROM action_items");
                statement.executeUpdate("DELETE FROM meetings");
                statement.executeUpdate("DELETE FROM task_tags");
                statement.executeUpdate("DELETE FROM task_assignees");
                statement.executeUpdate("DELETE FROM task_watchers");
                statement.executeUpdate("DELETE FROM task_comments");
                statement.executeUpdate("DELETE FROM tasks");
                statement.executeUpdate("DELETE FROM project_sequences");
                statement.executeUpdate("DELETE FROM tags");
                statement.executeUpdate("DELETE FROM project_members");
                statement.executeUpdate("DELETE FROM projects");
                statement.executeUpdate("DELETE FROM user_roles");
                statement.executeUpdate("DELETE FROM role_permissions");
                statement.executeUpdate("DELETE FROM refresh_tokens");
                statement.executeUpdate("DELETE FROM audit_logs");
                statement.executeUpdate("DELETE FROM users");
                statement.executeUpdate("DELETE FROM roles");
                statement.executeUpdate("DELETE FROM permissions");
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            return null;
        });

        adminId = createUser("admin.ai", "admin.ai@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "ADMIN", ADMIN_PERMS);
        pmUserId = createUser("pm.ai", "pm.ai@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "PROJECT_MANAGER", PM_PERMS);
        assigneeId = createUser("assignee.ai", "assignee.ai@pmdaily.local", PASSWORD, UserStatus.ACTIVE,
                "MEMBER", MEMBER_PERMS);
        outsiderId = createUser("outsider.ai", "outsider.ai@pmdaily.local", PASSWORD, UserStatus.ACTIVE,
                "MEMBER", MEMBER_PERMS);

        projectId = createProject("PRJ300", pmUserId);
        addMember(projectId, assigneeId, ProjectMemberRole.DEVELOPER);
        meetingId = createMeeting();
    }

    private UUID createUser(String username, String email, String password, UserStatus status,
            String roleCode, String... permissionCodes) {
        Set<Permission> permissions = new java.util.HashSet<>();
        for (String code : permissionCodes) {
            Permission permission = permissionRepository.findByCode(code).orElseGet(() -> {
                Permission created = new Permission();
                created.setCode(code);
                created.setName(code);
                return permissionRepository.save(created);
            });
            permissions.add(permission);
        }

        Role role = new Role();
        role.setCode("MEMBER".equals(roleCode) ? "MEMBER_" + username : roleCode);
        role.setName(roleCode + " " + username);
        role.setPermissions(permissions);
        roleRepository.save(role);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(status);
        user.getRoles().add(role);
        userRepository.save(user);

        return user.getId();
    }

    private String createProject(String code, UUID managerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"Dự án %s","description":"desc","status":"ACTIVE",
                                 "startDate":"2026-05-01","endDate":"2026-11-30","projectManagerId":"%s"}
                                """.formatted(code, code, managerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void addMember(String projectId, UUID userId, ProjectMemberRole role) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"%s"}
                                """.formatted(userId, role)))
                .andExpect(status().isCreated());
    }

    private String createMeeting() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Họp sprint 13","startTime":"2026-08-05T02:00:00Z",
                                 "endTime":"2026-08-05T03:00:00Z","location":"Phòng họp 1","chairpersonId":"%s"}
                                """.formatted(projectId, pmUserId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String actionItemBody(String title, String dueDate, String priority) {
        return """
                {"meetingId":"%s","projectId":"%s","title":"%s","description":"desc","assigneeId":"%s"%s%s}
                """.formatted(meetingId, projectId, title, assigneeId,
                dueDate == null ? "" : ",\"dueDate\":\"" + dueDate + "\"",
                priority == null ? "" : ",\"priority\":\"" + priority + "\"");
    }

    private String createActionItem(String title, String dueDate, String priority) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/action-items")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionItemBody(title, dueDate, priority)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private MvcResult loginAs(String username) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult admin() throws Exception {
        return loginAs("admin.ai");
    }

    private MvcResult pm() throws Exception {
        return loginAs("pm.ai");
    }

    private MvcResult assignee() throws Exception {
        return loginAs("assignee.ai");
    }

    private MvcResult outsider() throws Exception {
        return loginAs("outsider.ai");
    }

    private String bearer(MvcResult result) throws Exception {
        return "Bearer " + tokenFrom(result, "accessToken");
    }

    private String tokenFrom(MvcResult result, String field) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get(field).asText();
    }

    // ------------------------------------------------------------------ CRUD

    @Test
    void create_success_returns201WithDefaults() throws Exception {
        mockMvc.perform(post("/api/v1/action-items")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionItemBody("Theo dõi fix lỗi iOS", "2026-08-10", "HIGH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Theo dõi fix lỗi iOS"))
                .andExpect(jsonPath("$.meetingId").value(meetingId))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.assignee.fullName").value("assignee.ai"))
                .andExpect(jsonPath("$.dueDate").value("2026-08-10"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.progress").value(0))
                .andExpect(jsonPath("$.linkedTaskId").doesNotExist())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void create_defaultPriorityMedium() throws Exception {
        mockMvc.perform(post("/api/v1/action-items")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionItemBody("Việc không ưu tiên", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    void create_deletedMeeting_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/action-items")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"meetingId":"%s","projectId":"%s","title":"X","assigneeId":"%s"}
                                """.formatted(UUID.randomUUID(), projectId, assigneeId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_projectMismatch_returns400() throws Exception {
        String otherProjectId = createProject("PRJ301", pmUserId);
        mockMvc.perform(post("/api/v1/action-items")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"meetingId":"%s","projectId":"%s","title":"X","assigneeId":"%s"}
                                """.formatted(meetingId, otherProjectId, assigneeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void create_assigneeOutsideProject_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/action-items")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"meetingId":"%s","projectId":"%s","title":"X","assigneeId":"%s"}
                                """.formatted(meetingId, projectId, outsiderId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    void create_byMember_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/action-items")
                        .header("Authorization", bearer(assignee()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionItemBody("Việc cấm", null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_success_returnsDetail() throws Exception {
        String id = createActionItem("Việc chi tiết", "2026-08-10", null);
        mockMvc.perform(get("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(assignee())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Việc chi tiết"))
                .andExpect(jsonPath("$.assignee.fullName").value("assignee.ai"));
    }

    @Test
    void get_byOutsider_returns403() throws Exception {
        String id = createActionItem("Việc bí mật", null, null);
        mockMvc.perform(get("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(outsider())))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/action-items/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_filtersByStatusAndAssigneeAndKeyword() throws Exception {
        createActionItem("Việc A", "2026-08-10", "HIGH");
        createActionItem("Việc B", "2026-08-11", "LOW");

        mockMvc.perform(get("/api/v1/action-items")
                        .header("Authorization", bearer(pm()))
                        .param("keyword", "việc a")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Việc A"));

        mockMvc.perform(get("/api/v1/action-items")
                        .header("Authorization", bearer(outsider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void list_overdueFilter_returnsOnlyPastNotClosed() throws Exception {
        createActionItem("Việc quá hạn", LocalDate.now().minusDays(1).toString(), null);
        String done = createActionItem("Việc quá hạn đã xong", LocalDate.now().minusDays(2).toString(), null);
        mockMvc.perform(put("/api/v1/action-items/{id}", done)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE","version":0}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/action-items")
                        .header("Authorization", bearer(pm()))
                        .param("overdue", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Việc quá hạn"));
    }

    @Test
    void list_invalidSort_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/action-items")
                        .header("Authorization", bearer(pm()))
                        .param("sort", "hacked"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void overdue_returnsOnlyPastNotClosed() throws Exception {
        createActionItem("Việc quá hạn", LocalDate.now().minusDays(1).toString(), null);
        createActionItem("Việc chưa tới hạn", LocalDate.now().plusDays(1).toString(), null);

        mockMvc.perform(get("/api/v1/action-items/overdue")
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Việc quá hạn"));
    }

    // ---------------------------------------------------------------- update

    @Test
    void update_byManager_allFields_success() throws Exception {
        String id = createActionItem("Việc cũ", "2026-08-10", "LOW");
        mockMvc.perform(put("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Việc mới","description":"desc2","dueDate":"2026-08-20",
                                 "priority":"CRITICAL","status":"DONE","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Việc mới"))
                .andExpect(jsonPath("$.dueDate").value("2026-08-20"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void update_byAssignee_onlyStatusProgress() throws Exception {
        String id = createActionItem("Việc của tôi", "2026-08-10", null);
        mockMvc.perform(put("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(assignee()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"HACK","status":"IN_PROGRESS","progress":50,"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.progress").value(50))
                .andExpect(jsonPath("$.title").value("Việc của tôi"));
    }

    @Test
    void update_byAssignee_doneBelow100_returns400() throws Exception {
        String id = createActionItem("Việc gấp", "2026-08-10", null);
        mockMvc.perform(put("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(assignee()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE","progress":50,"version":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROGRESS_REQUIRED_FOR_DONE"));
    }

    @Test
    void update_staleVersion_returns409() throws Exception {
        String id = createActionItem("Việc version", null, null);
        mockMvc.perform(put("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"progress":30,"version":9}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void update_byOtherMember_returns403() throws Exception {
        String id = createActionItem("Việc của người khác", null, null);
        mockMvc.perform(put("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(outsider()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE","version":0}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_doneReopen_returns400() throws Exception {
        String id = createActionItem("Việc đã đóng", null, null);
        mockMvc.perform(put("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE","version":0}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN","version":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void delete_success_returns204ThenNotFound() throws Exception {
        String id = createActionItem("Việc xóa", null, null);
        mockMvc.perform(delete("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_byMember_returns403() throws Exception {
        String id = createActionItem("Việc giữ", null, null);
        mockMvc.perform(delete("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(assignee())))
                .andExpect(status().isForbidden());
    }

    // --------------------------------------------------------- convert to task

    @Test
    void convertToTask_success_returns201WithActionItemSource() throws Exception {
        String id = createActionItem("Theo dõi trạng thái fix lỗi", "2026-08-15", "HIGH");
        mockMvc.perform(post("/api/v1/action-items/{id}/convert-to-task", id)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Theo dõi trạng thái fix lỗi"))
                .andExpect(jsonPath("$.source").value("ACTION_ITEM"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value("2026-08-15"))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.assignee.fullName").value("assignee.ai"));

        mockMvc.perform(get("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedTaskId").isNotEmpty());
    }

    @Test
    void convertToTask_withOverrides() throws Exception {
        String id = createActionItem("Việc chuyển", "2026-08-15", "LOW");
        mockMvc.perform(post("/api/v1/action-items/{id}/convert-to-task", id)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dueDate":"2026-08-25","priority":"CRITICAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value("2026-08-25"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"));
    }

    @Test
    void convertToTask_alreadyLinked_returns409() throws Exception {
        String id = createActionItem("Việc đã chuyển", null, null);
        mockMvc.perform(post("/api/v1/action-items/{id}/convert-to-task", id)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/action-items/{id}/convert-to-task", id)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_LINKED"));
    }

    @Test
    void convertToTask_doneItem_returns400() throws Exception {
        String id = createActionItem("Việc đã xong", null, null);
        mockMvc.perform(put("/api/v1/action-items/{id}", id)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE","version":0}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/action-items/{id}/convert-to-task", id)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void convertToTask_byMember_returns403() throws Exception {
        String id = createActionItem("Việc cấm chuyển", null, null);
        mockMvc.perform(post("/api/v1/action-items/{id}/convert-to-task", id)
                        .header("Authorization", bearer(assignee()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------- meeting detail integration

    @Test
    void meetingDetail_includesActionItems() throws Exception {
        createActionItem("Việc từ họp", "2026-08-10", null);
        mockMvc.perform(get("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionItems.length()").value(1))
                .andExpect(jsonPath("$.actionItems[0].title").value("Việc từ họp"))
                .andExpect(jsonPath("$.actionItems[0].status").value("OPEN"))
                .andExpect(jsonPath("$.actionItems[0].assignee.fullName").value("assignee.ai"));
    }
}
