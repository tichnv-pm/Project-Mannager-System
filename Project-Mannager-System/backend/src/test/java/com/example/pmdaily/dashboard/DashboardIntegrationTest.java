package com.example.pmdaily.dashboard;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PM_PERMS =
            {"project:view", "project:create", "project:update", "project-member:manage", "dashboard:view", "task:view", "task:create"};

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
        pmUser = createUser("pm.dsh", "pm.dsh@example.com", pmRole);

        project = new Project();
        project.setCode("PRJ-DSH");
        project.setName("Dashboard Project Test");
        project.setStartDate(LocalDate.now());
        project.setEndDate(LocalDate.now().plusMonths(6));
        project.setProgress(45);
        project = projectRepository.save(project);

        com.example.pmdaily.project.ProjectMember member = new com.example.pmdaily.project.ProjectMember();
        member.setProject(project);
        member.setUser(pmUser);
        member.setRole(ProjectMemberRole.PROJECT_MANAGER);
        jdbcTemplate.update("INSERT INTO project_members (id, project_id, user_id, role, created_at, updated_at) VALUES (gen_random_uuid(), ?, ?, 'PROJECT_MANAGER', now(), now())",
                project.getId(), pmUser.getId());

        // Add 1 task today & 1 task in progress
        Task task = new Task();
        task.setCode("PRJ-DSH-TASK-001");
        task.setTitle("Dashboard Task Test");
        task.setProject(project);
        task.setAssignee(pmUser);
        task.setReporter(pmUser);
        task.setPriority(TaskPriority.HIGH);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setDueDate(LocalDate.now());
        taskRepository.save(task);
    }

    @Test
    void testDashboardEndpoints() throws Exception {
        String token = login("pm.dsh", PASSWORD);

        // 1. GET /summary
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + token)
                        .param("projectId", project.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasksToday").value(1))
                .andExpect(jsonPath("$.inProgressTasks").value(1));

        // 2. GET /task-stats
        mockMvc.perform(get("/api/v1/dashboard/task-stats")
                        .header("Authorization", "Bearer " + token)
                        .param("projectId", project.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasksByStatus", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.tasksByPriority", hasSize(greaterThanOrEqualTo(1))));

        // 3. GET /projects/progress
        mockMvc.perform(get("/api/v1/dashboard/projects/progress")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects", hasSize(1)))
                .andExpect(jsonPath("$.projects[0].progress").value(45));
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
