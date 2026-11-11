package com.example.pmdaily.sprint;

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
import com.example.pmdaily.user.*;
import com.example.pmdaily.task.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.example.pmdaily.PMDailyApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SprintIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private com.example.pmdaily.project.ProjectRepository projectRepository;
    @Autowired
    private SprintRepository sprintRepository;
    @Autowired
    private SprintBurndownScheduler burndownScheduler;

    private String pmToken;
    private UUID projectId;
    private UUID pmUserId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                String[] tables = {
                        "refresh_tokens", "task_assignees", "tasks", "sprint_burndown_snapshots", "sprints",
                        "project_members", "projects", "user_roles", "role_permissions", "permissions", "roles", "users"
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

        // Seed permission
        Permission p1 = new Permission();
        p1.setCode("project:view");
        p1.setName("View project");
        permissionRepository.save(p1);

        Permission p2 = new Permission();
        p2.setCode("project:update");
        p2.setName("Update project");
        permissionRepository.save(p2);

        // Role & Assign permissions
        Role pmRole = createRole("PROJECT_MANAGER", "PM Role", new String[]{"project:view", "project:update"});

        // User
        User pmUser = new User();
        pmUser.setUsername("pm.sprint");
        pmUser.setEmail("pm.sprint@pmdaily.com");
        pmUser.setFullName("Sprint Project Manager");
        pmUser.setPasswordHash(passwordEncoder.encode(PASSWORD));
        pmUser.setStatus(UserStatus.ACTIVE);
        pmUser.setRoles(Set.of(pmRole));
        pmUserId = userRepository.save(pmUser).getId();

        // Project
        projectId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, code, name, status, progress, start_date, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, now(), now(), 0)",
                projectId, "PRJ-SPRINT", "Agile Sprint Project", "ACTIVE", 0, LocalDate.now());

        // Member PM
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO project_members (id, project_id, user_id, role, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
                memberId, projectId, pmUserId, "PROJECT_MANAGER");

        // Login
        pmToken = login("pm.sprint");
    }

    @Test
    void testSprintLifecycle_Create_ActiveConstraint_CloseBacklogRollback() throws Exception {
        // 1. Create Sprint 1 (FUTURE)
        String sprint1Json = """
                {
                    "sprintName": "Sprint 1: Bootstrap",
                    "startDate": "2026-09-01",
                    "endDate": "2026-09-14",
                    "goal": "Build baseline framework"
                }
                """;

        MvcResult createRes1 = mockMvc.perform(post("/api/v1/projects/" + projectId + "/sprints")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sprint1Json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FUTURE"))
                .andExpect(jsonPath("$.sprintName").value("Sprint 1: Bootstrap"))
                .andReturn();
        
        UUID sprint1Id = UUID.fromString(objectMapper.readTree(createRes1.getResponse().getContentAsString()).get("id").asText());

        // 2. Create Sprint 2 (FUTURE)
        String sprint2Json = """
                {
                    "sprintName": "Sprint 2: Authentication",
                    "startDate": "2026-09-15",
                    "endDate": "2026-09-28",
                    "goal": "Build OAuth login flow"
                }
                """;

        MvcResult createRes2 = mockMvc.perform(post("/api/v1/projects/" + projectId + "/sprints")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sprint2Json))
                .andExpect(status().isCreated())
                .andReturn();
        
        UUID sprint2Id = UUID.fromString(objectMapper.readTree(createRes2.getResponse().getContentAsString()).get("id").asText());

        // 3. Start Sprint 1 (FUTURE -> ACTIVE)
        String startSprint1Json = """
                {
                    "sprintName": "Sprint 1: Bootstrap (Active)",
                    "startDate": "2026-09-01",
                    "endDate": "2026-09-14",
                    "status": "ACTIVE",
                    "goal": "Build framework"
                }
                """;

        mockMvc.perform(put("/api/v1/sprints/" + sprint1Id)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startSprint1Json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 4. Try starting Sprint 2 while Sprint 1 is ACTIVE (should fail with 409 Conflict)
        String startSprint2Json = """
                {
                    "sprintName": "Sprint 2: Authentication (Active)",
                    "startDate": "2026-09-15",
                    "endDate": "2026-09-28",
                    "status": "ACTIVE",
                    "goal": "OAuth login"
                }
                """;

        mockMvc.perform(put("/api/v1/sprints/" + sprint2Id)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startSprint2Json))
                .andExpect(status().isConflict());

        // 5. Add two tasks to Sprint 1 (one completed, one in_progress)
        com.example.pmdaily.project.Project project = projectRepository.findById(projectId).orElseThrow();
        User pm = userRepository.findById(pmUserId).orElseThrow();

        // 5. Add two tasks to Sprint 1 (one completed, one in_progress)
        Task completedTask = new Task();
        completedTask.setProject(project);
        completedTask.setReporter(pm);
        completedTask.setCode("TSK-SP1-01");
        completedTask.setTitle("Completed framework task");
        completedTask.setStatus(TaskStatus.DONE);
        completedTask.setSprintId(sprint1Id);
        completedTask.setCreatedBy(pmUserId);
        completedTask.setUpdatedBy(pmUserId);
        taskRepository.save(completedTask);

        Task unfinishedTask = new Task();
        unfinishedTask.setProject(project);
        unfinishedTask.setReporter(pm);
        unfinishedTask.setCode("TSK-SP1-02");
        unfinishedTask.setTitle("Unfinished backlog task");
        unfinishedTask.setStatus(TaskStatus.IN_PROGRESS);
        unfinishedTask.setSprintId(sprint1Id);
        unfinishedTask.setCreatedBy(pmUserId);
        unfinishedTask.setUpdatedBy(pmUserId);
        taskRepository.save(unfinishedTask);

        // 6. Close Sprint 1 (ACTIVE -> COMPLETED)
        String closeSprint1Json = """
                {
                    "sprintName": "Sprint 1: Bootstrap (Done)",
                    "startDate": "2026-09-01",
                    "endDate": "2026-09-14",
                    "status": "COMPLETED",
                    "goal": "Build framework"
                }
                """;

        mockMvc.perform(put("/api/v1/sprints/" + sprint1Id)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeSprint1Json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 7. Verify Task 1 (DONE) remains in Sprint 1, and Task 2 (IN_PROGRESS) rollback to Backlog (sprint_id = NULL)
        Task t1 = taskRepository.findById(completedTask.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(sprint1Id, t1.getSprintId());

        Task t2 = taskRepository.findById(unfinishedTask.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(t2.getSprintId());
    }

    @Test
    void velocity_computesFromStoryPointsAndDoneStatus() throws Exception {
        UUID sprintId = createActiveSprint("Sprint Velocity", "2026-10-01", "2026-10-14");
        com.example.pmdaily.project.Project project = projectRepository.findById(projectId).orElseThrow();
        User pm = userRepository.findById(pmUserId).orElseThrow();

        createSprintTask(project, pm, sprintId, "TSK-VEL-01", TaskStatus.DONE, 5);
        createSprintTask(project, pm, sprintId, "TSK-VEL-02", TaskStatus.DONE, 3);
        createSprintTask(project, pm, sprintId, "TSK-VEL-03", TaskStatus.IN_PROGRESS, 8);

        mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/velocity")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStoryPoints").value(16))
                .andExpect(jsonPath("$.completedStoryPoints").value(8))
                .andExpect(jsonPath("$.totalTasks").value(3))
                .andExpect(jsonPath("$.completedTasks").value(2));
    }

    @Test
    void burndown_returnsSnapshotsWithIdealLine() throws Exception {
        UUID sprintId = createActiveSprint("Sprint Burndown", "2026-10-01", "2026-10-11");
        com.example.pmdaily.project.Project project = projectRepository.findById(projectId).orElseThrow();
        User pm = userRepository.findById(pmUserId).orElseThrow();
        Sprint sprint = sprintRepository.findByIdAndDeletedAtIsNull(sprintId).orElseThrow();

        createSprintTask(project, pm, sprintId, "TSK-BD-01", TaskStatus.IN_PROGRESS, 10);
        createSprintTask(project, pm, sprintId, "TSK-BD-02", TaskStatus.IN_PROGRESS, 10);
        // Day 1 snapshot: nothing done yet
        burndownScheduler.calculateAndSaveSnapshot(sprint, LocalDate.parse("2026-10-01"));

        // Complete one task, day 6 snapshot (midpoint of a 10-day sprint)
        Task t1 = taskRepository.findByCodeAndDeletedAtIsNull("TSK-BD-01").orElseThrow();
        t1.setStatus(TaskStatus.DONE);
        taskRepository.save(t1);
        burndownScheduler.calculateAndSaveSnapshot(sprint, LocalDate.parse("2026-10-06"));

        mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/burndown")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$[0].actualRemainingStoryPoints").value(20))
                .andExpect(jsonPath("$[0].idealRemainingStoryPoints").value(20.0))
                .andExpect(jsonPath("$[1].date").value("2026-10-06"))
                .andExpect(jsonPath("$[1].actualRemainingStoryPoints").value(10))
                .andExpect(jsonPath("$[1].idealRemainingStoryPoints").value(10.0));
    }

    @Test
    void burndown_noSnapshotsYet_returnsEmptyList() throws Exception {
        UUID sprintId = createActiveSprint("Sprint Empty", "2026-11-01", "2026-11-14");

        mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/burndown")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private UUID createActiveSprint(String name, String startDate, String endDate) throws Exception {
        String json = """
                {"sprintName":"%s","startDate":"%s","endDate":"%s","status":"ACTIVE","goal":"g"}
                """.formatted(name, startDate, endDate);
        MvcResult result = mockMvc.perform(post("/api/v1/projects/" + projectId + "/sprints")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sprintName":"%s","startDate":"%s","endDate":"%s","goal":"g"}
                                """.formatted(name, startDate, endDate)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID sprintId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(put("/api/v1/sprints/" + sprintId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
        return sprintId;
    }

    private void createSprintTask(com.example.pmdaily.project.Project project, User reporter, UUID sprintId,
            String code, TaskStatus status, int storyPoints) {
        Task task = new Task();
        task.setProject(project);
        task.setReporter(reporter);
        task.setCode(code);
        task.setTitle(code);
        task.setStatus(status);
        task.setSprintId(sprintId);
        task.setStoryPoints(storyPoints);
        task.setCreatedBy(pmUserId);
        task.setUpdatedBy(pmUserId);
        taskRepository.save(task);
    }

    private String login(String username) {
        try {
            String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, PASSWORD);
            MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andReturn();
            return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Role createRole(String code, String name, String[] perms) {
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        for (String p : perms) {
            Permission perm = permissionRepository.findByCode(p)
                    .orElseGet(() -> {
                        Permission np = new Permission();
                        np.setCode(p);
                        np.setName(p);
                        return permissionRepository.save(np);
                    });
            role.getPermissions().add(perm);
        }
        return roleRepository.save(role);
    }
}
