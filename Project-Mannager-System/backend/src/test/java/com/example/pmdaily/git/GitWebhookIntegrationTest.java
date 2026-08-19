package com.example.pmdaily.git;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskRepository;
import com.example.pmdaily.task.TaskStatus;
import com.example.pmdaily.user.Permission;
import com.example.pmdaily.user.PermissionRepository;
import com.example.pmdaily.user.Role;
import com.example.pmdaily.user.RoleRepository;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.example.pmdaily.PMDailyApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GitWebhookIntegrationTest {

    private static final String SECRET = "git-webhook-secret-key-12345";
    private static final String PASSWORD = "Abc@12345";

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
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository memberRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private GitCommitRepository gitCommitRepository;
    @Autowired
    private GitPullRequestRepository gitPullRequestRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID pmUserId;
    private Project project;
    private Task task;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        gitCommitRepository.deleteAll();
        gitPullRequestRepository.deleteAll();
        taskRepository.deleteAll();
        memberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");

        // Seed permissions
        Permission viewPerm = new Permission();
        viewPerm.setCode("task:view");
        viewPerm.setName("View Task");
        permissionRepository.save(viewPerm);

        // Seed Role
        Role pmRole = new Role();
        pmRole.setCode("PROJECT_MANAGER");
        pmRole.setName("Project Manager");
        pmRole.setPermissions(java.util.Set.of(viewPerm));
        roleRepository.save(pmRole);

        // Seed User
        User pm = new User();
        pm.setUsername("pm.git");
        pm.setPasswordHash(passwordEncoder.encode(PASSWORD));
        pm.setFullName("PM Git");
        pm.setEmail("pm.git@example.com");
        pm.setStatus(UserStatus.ACTIVE);
        pm.setRoles(java.util.Set.of(pmRole));
        pm = userRepository.save(pm);
        pmUserId = pm.getId();

        // Seed Project
        project = new Project();
        project.setCode("PRJ");
        project.setName("Git Test Project");
        project.setCreatedBy(pmUserId);
        project.setUpdatedBy(pmUserId);
        project = projectRepository.save(project);

        // Seed Member
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(pm);
        member.setRole(ProjectMemberRole.PROJECT_MANAGER);
        member.setCreatedBy(pmUserId);
        member.setUpdatedBy(pmUserId);
        memberRepository.save(member);

        // Seed Task
        task = new Task();
        task.setProject(project);
        task.setCode("PRJ-TASK-1");
        task.setTitle("Git integration task");
        task.setStatus(TaskStatus.TODO);
        task.setProgress(0);
        task.setReporter(pm);
        task.setCreatedBy(pmUserId);
        task.setUpdatedBy(pmUserId);
        task = taskRepository.save(task);
    }

    @Test
    void testGitWebhookBypassesJwtAuthentication() throws Exception {
        // Calling webhook without JWT bearer token, expect 401 only due to missing/invalid signature
        mockMvc.perform(post("/api/v1/public/webhooks/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGitHubWebhookAuthenticatesAndLinksCommit() throws Exception {
        String payload = """
                {
                    "commits": [
                        {
                            "id": "abc123commitsha",
                            "message": "[PRJ-TASK-1] Build database migrations",
                            "author": {
                                "name": "github-tester",
                                "username": "github-tester"
                            },
                            "url": "http://github/commit/abc123commitsha"
                        }
                    ]
                }
                """;
        
        String sig = "sha256=" + calculateHmac(payload, SECRET);

        mockMvc.perform(post("/api/v1/public/webhooks/git")
                        .header("X-Hub-Signature-256", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Verify commit is saved
        List<GitCommit> commits = gitCommitRepository.findByTaskIdOrderByCreatedAtDesc(task.getId());
        assertEquals(1, commits.size());
        assertEquals("abc123commitsha", commits.get(0).getCommitHash());
        assertEquals("[PRJ-TASK-1] Build database migrations", commits.get(0).getMessage());
        assertEquals("github-tester", commits.get(0).getAuthor());
    }

    @Test
    void testGitHubWebhookFailsWithInvalidHmac() throws Exception {
        String payload = "{}";
        mockMvc.perform(post("/api/v1/public/webhooks/git")
                        .header("X-Hub-Signature-256", "sha256=invalidhashvalue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGitLabWebhookAuthenticatesAndLinksCommit() throws Exception {
        String payload = """
                {
                    "commits": [
                        {
                            "id": "gitlabcommitsha456",
                            "message": "[PRJ-TASK-1] Setup GitLab webhook controller",
                            "author": {
                                "name": "gitlab-tester"
                            },
                            "url": "http://gitlab/commit/gitlabcommitsha456"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/public/webhooks/git")
                        .header("X-Gitlab-Token", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        List<GitCommit> commits = gitCommitRepository.findByTaskIdOrderByCreatedAtDesc(task.getId());
        assertEquals(1, commits.size());
        assertEquals("gitlabcommitsha456", commits.get(0).getCommitHash());
    }

    @Test
    void testGitHubPullRequestAutoCompletesTaskOnMerge() throws Exception {
        String payload = """
                {
                    "action": "closed",
                    "pull_request": {
                        "number": 42,
                        "title": "[PRJ-TASK-1] PR description text",
                        "state": "closed",
                        "merged": true,
                        "html_url": "http://github/pr/42"
                    }
                }
                """;

        String sig = "sha256=" + calculateHmac(payload, SECRET);

        mockMvc.perform(post("/api/v1/public/webhooks/git")
                        .header("X-Hub-Signature-256", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Verify task status transitioned to DONE and 100% progress
        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.DONE, updatedTask.getStatus());
        assertEquals(100, updatedTask.getProgress());

        // Verify PR entry is saved
        List<GitPullRequest> prs = gitPullRequestRepository.findByTaskIdOrderByCreatedAtDesc(task.getId());
        assertEquals(1, prs.size());
        assertEquals(42, prs.get(0).getPrNumber());
        assertEquals("CLOSED", prs.get(0).getStatus());
    }

    @Test
    void testQueryGitInfoForTask() throws Exception {
        // Save test commit & PR directly
        GitCommit commit = new GitCommit();
        commit.setTask(task);
        commit.setCommitHash("hash999");
        commit.setMessage("[PRJ-TASK-1] direct insert");
        commit.setAuthor("db-insert");
        commit.setCommitUrl("http://local/999");
        gitCommitRepository.save(commit);

        GitPullRequest pr = new GitPullRequest();
        pr.setTask(task);
        pr.setPrNumber(10);
        pr.setTitle("[PRJ-TASK-1] pr 10");
        pr.setStatus("OPEN");
        pr.setPrUrl("http://local/pr/10");
        gitPullRequestRepository.save(pr);

        String token = login("pm.git", PASSWORD);

        mockMvc.perform(get("/api/v1/tasks/" + task.getId() + "/git")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commits", hasSize(1)))
                .andExpect(jsonPath("$.commits[0].commitHash", is("hash999")))
                .andExpect(jsonPath("$.pullRequests", hasSize(1)))
                .andExpect(jsonPath("$.pullRequests[0].prNumber", is(10)));
    }

    private String login(String username, String password) throws Exception {
        String json = objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String calculateHmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : rawHmac) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
