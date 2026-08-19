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
import com.example.pmdaily.project.ProjectMemberRole;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PM_PERMS =
            {"project:view", "project:create", "project-member:manage", "report:view", "report:export", "task:view"};

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
    private Project project;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
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
        pmUser = createUser("pm.rpt", "pm.rpt@example.com", pmRole);

        project = new Project();
        project.setCode("PRJ-RPT");
        project.setName("Report Project Test");
        project.setStartDate(LocalDate.now());
        project.setEndDate(LocalDate.now().plusMonths(6));
        project.setProgress(60);
        project = projectRepository.save(project);

        jdbcTemplate.update("INSERT INTO project_members (id, project_id, user_id, role, created_at, updated_at) VALUES (gen_random_uuid(), ?, ?, 'PROJECT_MANAGER', now(), now())",
                project.getId(), pmUser.getId());

        // Add 1 task
        Task task = new Task();
        task.setCode("PRJ-RPT-TASK-001");
        task.setTitle("Report Task Test");
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
    void testReportEndpointsAndExport() throws Exception {
        String token = login("pm.rpt", PASSWORD);

        // 1. GET /tasks-by-status
        mockMvc.perform(get("/api/v1/reports/tasks-by-status")
                        .header("Authorization", "Bearer " + token)
                        .param("projectId", project.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))));

        // 2. GET /tasks-by-assignee
        mockMvc.perform(get("/api/v1/reports/tasks-by-assignee")
                        .header("Authorization", "Bearer " + token)
                        .param("projectId", project.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))));

        // 3. GET /project-progress
        mockMvc.perform(get("/api/v1/reports/project-progress")
                        .header("Authorization", "Bearer " + token)
                        .param("projectId", project.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].progress").value(60));

        // 4. GET /risk-issue-summary
        mockMvc.perform(get("/api/v1/reports/risk-issue-summary")
                        .header("Authorization", "Bearer " + token)
                        .param("projectId", project.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openRisks").value(0))
                .andExpect(jsonPath("$.openIssues").value(0));

        // 5. GET /export -> CSV (streaming: StreamingResponseBody vẫn sinh đủ body qua MockMvc)
        mockMvc.perform(get("/api/v1/reports/export")
                        .header("Authorization", "Bearer " + token)
                        .param("report", "tasks-by-status")
                        .param("format", "csv")
                        .param("projectId", project.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"report-tasks-by-status.csv\""))
                .andExpect(content().string(containsString("Report: tasks-by-status")))
                .andExpect(content().string(containsString("Status,Count")));
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
}
