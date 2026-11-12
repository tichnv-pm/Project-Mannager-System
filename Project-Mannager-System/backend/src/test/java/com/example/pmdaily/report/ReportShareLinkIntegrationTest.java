package com.example.pmdaily.report;

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

import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskPriority;
import com.example.pmdaily.task.TaskRepository;
import com.example.pmdaily.task.TaskStatus;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GĐ4 (3/3) — share-link báo cáo tiến độ cho stakeholder ngoài tổ chức (docs/api/12-report-audit-api.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportShareLinkIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PM_PERMS =
            {"project:view", "project:create", "project-member:manage", "report:view", "report:share", "task:view"};
    private static final String[] MEMBER_PERMS = {"project:view", "report:view", "task:view"};

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
    private TaskRepository taskRepository;

    private User pmUser;
    private User memberUser;
    private Project project;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                statement.executeUpdate("DELETE FROM report_share_links");
                statement.executeUpdate("DELETE FROM tasks");
                statement.executeUpdate("DELETE FROM project_members");
                statement.executeUpdate("DELETE FROM projects");
                statement.executeUpdate("DELETE FROM user_roles");
                statement.executeUpdate("DELETE FROM role_permissions");
                statement.executeUpdate("DELETE FROM permissions");
                statement.executeUpdate("DELETE FROM roles");
                statement.executeUpdate("DELETE FROM users");
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            return null;
        });

        Role pmRole = createRole("PROJECT_MANAGER", "PM", PM_PERMS);
        Role memberRole = createRole("PROJECT_MEMBER", "Member", MEMBER_PERMS);
        pmUser = createUser("pm.shr", "pm.shr@example.com", pmRole);
        memberUser = createUser("member.shr", "member.shr@example.com", memberRole);

        project = new Project();
        project.setCode("PRJ-SHR");
        project.setName("Share Link Project Test");
        project.setStartDate(LocalDate.now());
        project.setEndDate(LocalDate.now().plusMonths(6));
        project.setProgress(42);
        project = projectRepository.save(project);

        jdbcTemplate.update("INSERT INTO project_members (id, project_id, user_id, role, created_at, updated_at) "
                        + "VALUES (gen_random_uuid(), ?, ?, 'PROJECT_MANAGER', now(), now())",
                project.getId(), pmUser.getId());
        jdbcTemplate.update("INSERT INTO project_members (id, project_id, user_id, role, created_at, updated_at) "
                        + "VALUES (gen_random_uuid(), ?, ?, 'MEMBER', now(), now())",
                project.getId(), memberUser.getId());

        Task task = new Task();
        task.setCode("PRJ-SHR-TASK-001");
        task.setTitle("Share Link Task Test");
        task.setProject(project);
        task.setAssignee(pmUser);
        task.setReporter(pmUser);
        task.setPriority(TaskPriority.HIGH);
        task.setStatus(TaskStatus.DONE);
        task.setActualCompletedAt(java.time.Instant.now());
        task.setProgress(100);
        task.setDueDate(LocalDate.now());
        taskRepository.save(task);
    }

    @Test
    void create_view_revoke_fullCycle() throws Exception {
        String pm = login("pm.shr", PASSWORD);

        MvcResult createRes = mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/report-share-links")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Báo cáo cho khách hàng ABC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.label").value("Báo cáo cho khách hàng ABC"))
                .andReturn();
        String token = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        // Xem công khai — không cần Authorization header
        mockMvc.perform(get("/api/v1/public/shared-reports/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCode").value("PRJ-SHR"))
                .andExpect(jsonPath("$.progress").value(42))
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.doneTasks").value(1))
                .andExpect(jsonPath("$.label").value("Báo cáo cho khách hàng ABC"));

        // PM xem lại danh sách link của project
        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/report-share-links")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(token));

        // Thu hồi
        mockMvc.perform(delete("/api/v1/report-share-links/" + token)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isNoContent());

        // Sau khi thu hồi -> public xem không được nữa (404, không phân biệt lý do)
        mockMvc.perform(get("/api/v1/public/shared-reports/" + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_byMember_returns403() throws Exception {
        String member = login("member.shr", PASSWORD);

        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/report-share-links")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicView_expiredLink_returns404() throws Exception {
        String pm = login("pm.shr", PASSWORD);

        MvcResult createRes = mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/report-share-links")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/public/shared-reports/" + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicView_unknownToken_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/public/shared-reports/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
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
}
