package com.example.pmdaily.qa;

import java.time.LocalDate;
import java.util.List;
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

import com.example.pmdaily.issue.Issue;
import com.example.pmdaily.issue.IssueRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.user.*;
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
class QaIntegrationTest {

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
    private IssueRepository issueRepository;

    private String pmToken;
    private UUID projectId;
    private UUID pmUserId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                String[] tables = {
                        "refresh_tokens", "task_assignees", "tasks", "issues", "test_results", "test_runs",
                        "test_steps", "test_cases", "project_members", "projects", "user_roles",
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
        pmUser.setUsername("pm.qa");
        pmUser.setEmail("pm.qa@pmdaily.com");
        pmUser.setFullName("QA Manager");
        pmUser.setPasswordHash(passwordEncoder.encode(PASSWORD));
        pmUser.setStatus(UserStatus.ACTIVE);
        pmUser.setRoles(Set.of(pmRole));
        pmUserId = userRepository.save(pmUser).getId();

        // Project
        projectId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, code, name, status, progress, start_date, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, now(), now(), 0)",
                projectId, "PRJ-QA", "QA Quality Project", "ACTIVE", 0, LocalDate.now());

        // Member PM
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO project_members (id, project_id, user_id, role, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
                memberId, projectId, pmUserId, "PROJECT_MANAGER");

        // Login
        pmToken = login("pm.qa");
    }

    @Test
    void testQaLifecycle_CreateTestCase_CreateRun_ExecuteFailed_AutoBugCreated() throws Exception {
        // 1. Create a Test Case with Steps
        String testCaseJson = """
                {
                    "title": "TC001: User Login Verification",
                    "description": "Verify that user can log in with valid credentials.",
                    "preconditions": "User is registered and active.",
                    "priority": "HIGH",
                    "steps": [
                        {
                            "stepNumber": 1,
                            "action": "Navigate to login screen",
                            "expectedResult": "Login screen is displayed."
                        },
                        {
                            "stepNumber": 2,
                            "action": "Enter correct username and password and click login",
                            "expectedResult": "User dashboard is displayed."
                        }
                    ]
                }
                """;

        MvcResult createCaseRes = mockMvc.perform(post("/api/v1/projects/" + projectId + "/qa/test-cases")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testCaseJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("TC001: User Login Verification"))
                .andExpect(jsonPath("$.steps", hasSize(2)))
                .andReturn();

        UUID testCaseId = UUID.fromString(objectMapper.readTree(createCaseRes.getResponse().getContentAsString()).get("id").asText());

        // 2. Create a Test Run
        String testRunJson = """
                {
                    "name": "Regression Test Run v1.0.0",
                    "description": "Perform regression testing prior to release.",
                    "testCaseIds": ["%s"]
                }
                """.formatted(testCaseId);

        MvcResult createRunRes = mockMvc.perform(post("/api/v1/projects/" + projectId + "/qa/test-runs")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testRunJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Regression Test Run v1.0.0"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        UUID testRunId = UUID.fromString(objectMapper.readTree(createRunRes.getResponse().getContentAsString()).get("id").asText());

        // 3. Verify Test Results list contains the test case as UNTESTED
        mockMvc.perform(get("/api/v1/qa/test-runs/" + testRunId + "/results")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("UNTESTED"))
                .andExpect(jsonPath("$[0].testCaseTitle").value("TC001: User Login Verification"));

        // 4. Update Test Result to FAILED
        String updateResultJson = """
                {
                    "status": "FAILED",
                    "actualResult": "Redirected to error page: 500 Internal Server Error."
                }
                """;

        mockMvc.perform(put("/api/v1/qa/test-runs/" + testRunId + "/results/" + testCaseId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateResultJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.actualResult").value("Redirected to error page: 500 Internal Server Error."));

        // 5. Verify that a BUG issue was automatically created
        List<Issue> issues = issueRepository.findAll();
        org.junit.jupiter.api.Assertions.assertFalse(issues.isEmpty());
        
        Issue bug = issues.stream()
                .filter(i -> i.getTestCaseId() != null && i.getTestCaseId().equals(testCaseId))
                .findFirst()
                .orElse(null);

        org.junit.jupiter.api.Assertions.assertNotNull(bug);
        org.junit.jupiter.api.Assertions.assertTrue(bug.getTitle().contains("[BUG] Thất bại tại kiểm thử: TC001: User Login Verification"));
        org.junit.jupiter.api.Assertions.assertEquals("HIGH", bug.getSeverity().name());
        org.junit.jupiter.api.Assertions.assertEquals("OPEN", bug.getStatus().name());
        org.junit.jupiter.api.Assertions.assertTrue(bug.getDescription().contains("Redirected to error page: 500 Internal Server Error."));
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
