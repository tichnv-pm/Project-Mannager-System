package com.example.pmdaily.task;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMember;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test Task module (docs/api/05-task-api.md, BR-TASK).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] ADMIN_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage",
                    "task:view", "task:create", "task:update", "task:delete", "task:assign",
                    "task:comment", "task:attachment", "task:export"};
    private static final String[] PM_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage",
                    "task:view", "task:create", "task:update", "task:delete", "task:assign",
                    "task:comment", "task:attachment", "task:export"};
    private static final String[] MEMBER_PERMS =
            {"task:view", "task:create", "task:update", "task:comment", "task:attachment"};

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
    private TagRepository tagRepository;
    @Autowired
    private TaskRepository taskRepository;

    private UUID adminId;
    private UUID pmUserId;
    private UUID memberId;
    private UUID outsiderId;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                statement.executeUpdate("DELETE FROM task_tags");
                statement.executeUpdate("DELETE FROM task_assignees");
                statement.executeUpdate("DELETE FROM task_watchers");
                statement.executeUpdate("DELETE FROM task_comments");
                statement.executeUpdate("DELETE FROM attachments");
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

        adminId = createUser("admin.tsk", "admin.tsk@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "ADMIN", ADMIN_PERMS);
        pmUserId = createUser("pm.tsk", "pm.tsk@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "PROJECT_MANAGER", PM_PERMS);
        memberId = createUser("member.tsk", "member.tsk@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "MEMBER", MEMBER_PERMS);
        outsiderId = createUser("outsider.tsk", "outsider.tsk@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "MEMBER", MEMBER_PERMS);

        projectId = createProject("PRJ100", pmUserId);
        addMember(projectId, memberId, ProjectMemberRole.DEVELOPER);
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
        String body = managerId != null
                ? """
                        {"code":"%s","name":"Dự án %s","description":"desc","status":"ACTIVE",
                         "startDate":"2026-05-01","endDate":"2026-11-30","projectManagerId":"%s"}
                        """.formatted(code, code, managerId)
                : """
                        {"code":"%s","name":"Dự án %s","description":"desc","status":"PLANNING"}
                        """.formatted(code, code);
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
        return loginAs("admin.tsk");
    }

    private MvcResult pm() throws Exception {
        return loginAs("pm.tsk");
    }

    private MvcResult member() throws Exception {
        return loginAs("member.tsk");
    }

    private MvcResult outsider() throws Exception {
        return loginAs("outsider.tsk");
    }

    private String bearer(MvcResult result) throws Exception {
        return "Bearer " + tokenFrom(result, "accessToken");
    }

    private String tokenFrom(MvcResult result, String field) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get(field).asText();
    }

    private String createTask() throws Exception {
        return createTask(projectId, pmUserId);
    }

    private String createTask(String projectId, UUID assigneeId) throws Exception {
        String assignee = assigneeId != null ? "\"assigneeId\":\"%s\",".formatted(assigneeId) : "";
        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s",%s"title":"Task mẫu","priority":"HIGH",
                                 "startDate":"2026-07-01","dueDate":"2026-07-31"}
                                """.formatted(projectId, assignee)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTag() throws Exception {
        Tag tag = new Tag();
        tag.setName("backend-" + UUID.randomUUID());
        tag.setColor("#2196f3");
        return tagRepository.save(tag).getId().toString();
    }

    // ------------------------------------------------------------------ CRUD

    @Test
    void create_success_returns201_withAutoCode() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Xây màn hình login","priority":"HIGH",
                                 "type":"FEATURE","startDate":"2026-07-20","dueDate":"2026-08-05",
                                 "estimateMinutes":480,"assigneeId":"%s"}
                                """.formatted(projectId, memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PRJ100-TASK-000001"))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.projectCode").value("PRJ100"))
                .andExpect(jsonPath("$.projectName").isNotEmpty())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.type").value("FEATURE"))
                .andExpect(jsonPath("$.progress").value(0))
                .andExpect(jsonPath("$.reporter.id").value(pmUserId.toString()))
                .andExpect(jsonPath("$.assignee.id").value(memberId.toString()))
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void create_assigneeNotInProject_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Sai assignee","assigneeId":"%s"}
                                """.formatted(projectId, outsiderId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    void create_parentDifferentProject_returns400() throws Exception {
        String otherProject = createProject("PRJ200", pmUserId);
        String parentTaskId = createTask(otherProject, null);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Con sai dự án","parentTaskId":"%s"}
                                """.formatted(projectId, parentTaskId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARENT_TASK_PROJECT_MISMATCH"));
    }

    @Test
    void create_blockedWithoutReason_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Block thiếu lý do","blocked":true}
                                """.formatted(projectId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOCKER_REASON_REQUIRED"));
    }

    @Test
    void create_invalidDateRange_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Sai ngày","startDate":"2026-08-01",
                                 "dueDate":"2026-07-01"}
                                """.formatted(projectId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void create_byMember_success() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Member tạo task"}
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reporter.id").value(memberId.toString()));
    }

    @Test
    void get_success_returns200() throws Exception {
        String taskId = createTask();

        mockMvc.perform(get("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRJ100-TASK-000001"));
    }

    @Test
    void get_notMember_returns403() throws Exception {
        String taskId = createTask();

        mockMvc.perform(get("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", bearer(outsider())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void get_deletedTask_returns404() throws Exception {
        String taskId = createTask();
        mockMvc.perform(delete("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_filtersByStatusAndKeyword() throws Exception {
        String taskId = createTask();
        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk());
        createTask(projectId, null);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.content[0].projectCode").value("PRJ100"))
                .andExpect(jsonPath("$.content[0].projectName").isNotEmpty());

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .param("keyword", "Task mẫu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void list_invalidSort_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .param("sort", "hacked"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_invalidPageSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_memberWithoutAdminRole_seesOnlyJoinedProjects() throws Exception {
        createTask();

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", bearer(outsider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void update_success_returns200() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Đổi tên task","priority":"CRITICAL","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Đổi tên task"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void update_staleVersion_returns409() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Version cũ","version":99}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void update_doneWithProgressBelow100_returns400() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Done thiếu tiến độ","status":"DONE","progress":50,"version":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROGRESS_REQUIRED_FOR_DONE"));
    }

    @Test
    void update_member_onlyOwnAssignedTaskLimitedFields() throws Exception {
        String ownTask = createTask(projectId, memberId);
        String otherTask = createTask(projectId, pmUserId);

        mockMvc.perform(put("/api/v1/tasks/{id}", ownTask)
                        .header("Authorization", bearer(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Tên khác (bị bỏ qua)","status":"IN_PROGRESS",
                                 "notes":"Ghi chú member","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.notes").value("Ghi chú member"))
                .andExpect(jsonPath("$.title").value("Task mẫu"));

        mockMvc.perform(put("/api/v1/tasks/{id}", otherTask)
                        .header("Authorization", bearer(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Task người khác","status":"IN_PROGRESS","version":0}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void delete_success_returns204() throws Exception {
        String taskId = createTask();
        mockMvc.perform(delete("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_withChildren_returns400() throws Exception {
        String parentId = createTask();
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Task con","parentTaskId":"%s"}
                                """.formatted(projectId, parentId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/tasks/{id}", parentId)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void delete_byMember_returns403() throws Exception {
        String taskId = createTask();
        mockMvc.perform(delete("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isForbidden());
    }

    // --------------------------------------------------------- sub-resources

    @Test
    void assignee_change_success() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/assignee", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":"%s"}
                                """.formatted(pmUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.id").value(pmUserId.toString()));
    }

    @Test
    void assignee_notInProject_returns400() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/assignee", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":"%s"}
                                """.formatted(outsiderId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    void status_transition_validFlow_returns200() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REVIEW"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW"));

        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.actualCompletedAt").isNotEmpty());
    }

    @Test
    void status_invalidTransition_returns400() throws Exception {
        String taskId = createTask();
        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REVIEW"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"TODO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void status_blockedWithoutReason_returns400() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"BLOCKED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOCKER_REASON_REQUIRED"));
    }

    @Test
    void status_blockedWithReason_setsBlockedFlag() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"BLOCKED","blockerReason":"Chờ bên thứ ba"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.blocked").value(true));
    }

    @Test
    void progress_outOfRange_returns400() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/progress", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"progress":150}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void progress_doneTaskBelow100_returns400() throws Exception {
        String taskId = createTask();
        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REVIEW"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/tasks/{id}/progress", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"progress":50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROGRESS_REQUIRED_FOR_DONE"));
    }

    @Test
    void blocker_setWithoutReason_returns400() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/blocker", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blocked":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOCKER_REASON_REQUIRED"));
    }

    @Test
    void blocker_setAndClear_success() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/blocker", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blocked":true,"blockerReason":"Chờ API"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.blockerReason").value("Chờ API"));

        mockMvc.perform(put("/api/v1/tasks/{id}/blocker", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blocked":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void children_list_returns200() throws Exception {
        String parentId = createTask();
        MvcResult childResult = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Task con","parentTaskId":"%s"}
                                """.formatted(projectId, parentId)))
                .andExpect(status().isCreated())
                .andReturn();
        String childId = objectMapper.readTree(childResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/tasks/{id}/children", parentId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(childId))
                .andExpect(jsonPath("$[0].parentTaskId").value(parentId));
    }

    @Test
    void tags_replace_success() throws Exception {
        String taskId = createTask();
        String tagId = createTag();

        mockMvc.perform(put("/api/v1/tasks/{id}/tags", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":["%s"]}
                                """.formatted(tagId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].id").value(tagId));
    }

    @Test
    void tags_notFound_returns404() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/tags", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":["%s"]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void collaborators_replace_success() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/collaborators", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["%s"]}
                                """.formatted(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collaborators[0].id").value(memberId.toString()));
    }

    @Test
    void collaborators_notInProject_returns400() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/collaborators", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["%s"]}
                                """.formatted(outsiderId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    void watchers_replace_success() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/watchers", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["%s"]}
                                """.formatted(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watchers[0].id").value(memberId.toString()));
    }

    // ------------------------------------------------------------- comments

    @Test
    void comment_addListUpdateDelete_success() throws Exception {
        String taskId = createTask();

        MvcResult added = mockMvc.perform(post("/api/v1/tasks/{id}/comments", taskId)
                        .header("Authorization", bearer(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Bình luận đầu tiên"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id").value(memberId.toString()))
                .andReturn();
        String commentId = objectMapper.readTree(added.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/tasks/{id}/comments", taskId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Bình luận đầu tiên"));

        mockMvc.perform(put("/api/v1/tasks/{id}/comments/{commentId}", taskId, commentId)
                        .header("Authorization", bearer(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Đã sửa"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Đã sửa"));

        mockMvc.perform(delete("/api/v1/tasks/{id}/comments/{commentId}", taskId, commentId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/{id}/comments", taskId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void comment_update_notAuthor_returns403() throws Exception {
        String taskId = createTask();
        MvcResult added = mockMvc.perform(post("/api/v1/tasks/{id}/comments", taskId)
                        .header("Authorization", bearer(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Của member"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String commentId = objectMapper.readTree(added.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(put("/api/v1/tasks/{id}/comments/{commentId}", taskId, commentId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Cướp comment"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void comment_contentTooLong_returns400() throws Exception {
        String taskId = createTask();
        String longContent = "a".repeat(2001);

        mockMvc.perform(post("/api/v1/tasks/{id}/comments", taskId)
                        .header("Authorization", bearer(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(longContent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------------------------------------------------------- attachments

    @Test
    void attachment_uploadListDownloadDelete_success() throws Exception {
        String taskId = createTask();
        MockMultipartFile file = new MockMultipartFile("file", "ket-qua.txt",
                "text/plain", "nội dung test".getBytes());

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/tasks/{id}/attachments", taskId)
                        .file(file)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("ket-qua.txt"))
                .andExpect(jsonPath("$.sizeBytes").value(15))
                .andReturn();
        JsonNode attachmentJson = objectMapper.readTree(uploaded.getResponse().getContentAsString());
        String attachmentId = attachmentJson.get("id").asText();
        String filePath = attachmentJson.get("filePath").asText();

        mockMvc.perform(get("/api/v1/tasks/{id}/attachments", taskId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("ket-qua.txt"));

        mockMvc.perform(get(filePath)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/tasks/{id}/attachments/{attachmentId}", taskId, attachmentId)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/{id}/attachments", taskId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void attachment_unsupportedMime_returns400() throws Exception {
        String taskId = createTask();
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe",
                "application/x-msdownload", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/tasks/{id}/attachments", taskId)
                        .file(file)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void attachment_tooLarge_returns413() throws Exception {
        String taskId = createTask();
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf",
                "application/pdf", new byte[11 * 1024 * 1024]);

        mockMvc.perform(multipart("/api/v1/tasks/{id}/attachments", taskId)
                        .file(file)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    // -------------------------------------------------------------- history

    @Test
    void history_returnsChangesAfterUpdate() throws Exception {
        String taskId = createTask();

        mockMvc.perform(put("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/{id}/history", taskId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("TASK_CREATED"))
                .andExpect(jsonPath("$[1].action").value("TASK_STATUS_CHANGE"))
                .andExpect(jsonPath("$[1].changes.status.from").value("TODO"))
                .andExpect(jsonPath("$[1].changes.status.to").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].changedByUsername").value("pm.tsk"));
    }

    // --------------------------------------------------------------- export

    @Test
    void export_returnsXlsx() throws Exception {
        createTask();

        mockMvc.perform(get("/api/v1/tasks/export")
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isOk())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getContentType().contains("spreadsheetml")))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getHeader("Content-Disposition").contains(".xlsx")));
    }

    @Test
    void export_member_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/export")
                        .header("Authorization", bearer(member())))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- my-tasks

    @Test
    void myTasks_today_overdue_returnsCorrectData() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Task hôm nay","assigneeId":"%s",
                                 "dueDate":"%s"}
                                """.formatted(projectId, memberId, java.time.LocalDate.now())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Task quá hạn","assigneeId":"%s",
                                 "dueDate":"2026-01-01"}
                                """.formatted(projectId, memberId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tasks/my-tasks")
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/tasks/today")
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Task hôm nay"));

        mockMvc.perform(get("/api/v1/tasks/overdue")
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Task quá hạn"));
    }

    // ------------------------------------------------------- concurrent code

    @Test
    void concurrent_create_differentProjects_sequentialCodesPerProject() throws Exception {
        String projectA = createProject("PRJ300", pmUserId);
        String projectB = createProject("PRJ301", pmUserId);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/tasks")
                            .header("Authorization", bearer(pm()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"projectId":"%s","title":"Task A %d"}
                                    """.formatted(projectA, i)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(
                            String.format("PRJ300-TASK-%06d", i + 1)));
            mockMvc.perform(post("/api/v1/tasks")
                            .header("Authorization", bearer(pm()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"projectId":"%s","title":"Task B %d"}
                                    """.formatted(projectB, i)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(
                            String.format("PRJ301-TASK-%06d", i + 1)));
        }
    }
}
