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
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests PLN-BE-10 — Plan Template & Portfolio (docs/planning/12 §2.1-2.4, docs/planning/06, docs/api/13-planning-api.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanTemplatePortfolioIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PROJECT_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};
    private static final String[] PLAN_PERMS =
            {"plan:view", "plan:create", "plan:update", "plan:delete", "plan:approve", "plan:schedule"};

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
    private UUID pmUserId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                String[] tables = {
                        "plan_template_tasks", "plan_templates",
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

        Role adminRole = createRole("ADMIN", "Admin", concat(PROJECT_PERMS, PLAN_PERMS));
        Role pmRole = createRole("PROJECT_MANAGER", "PM", concat(PROJECT_PERMS, PLAN_PERMS));

        User admin = createUser("admin.tpl", "admin.tpl@example.com", adminRole);
        User pm = createUser("pm.tpl", "pm.tpl@example.com", pmRole);

        projectId = createProject(adminToken(), "PRJ-TPL", "Project Template Test");
        addMember(adminToken(), projectId, pm.getId(), ProjectMemberRole.PROJECT_MANAGER);
        pmUserId = pm.getId();
    }

    @Test
    void testGetAllTemplatesAndDetail() throws Exception {
        String pm = login("pm.tpl", PASSWORD);

        // Fetch all templates
        MvcResult res = mockMvc.perform(get("/api/v1/plan-templates")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$[*].templateCode", hasItem("FULL_SDL")))
                .andReturn();

        String content = res.getResponse().getContentAsString();
        String fullSdlId = objectMapper.readTree(content).get(0).get("id").asText();

        // Fetch template detail
        mockMvc.perform(get("/api/v1/plan-templates/" + fullSdlId)
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()", is(17)))
                .andExpect(jsonPath("$.tasks[0].taskName", is("1. INITIATION")))
                .andExpect(jsonPath("$.tasks[16].taskName", is("17. CLOSURE")));
    }

    @Test
    void testCreatePlanFromTemplate() throws Exception {
        String pm = login("pm.tpl", PASSWORD);

        // Get FULL_SDL template ID
        MvcResult res = mockMvc.perform(get("/api/v1/plan-templates")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andReturn();
        String content = res.getResponse().getContentAsString();
        String templateId = objectMapper.readTree(content).get(0).get("id").asText();

        // Create Plan from Template
        MvcResult createRes = mockMvc.perform(post("/api/v1/plans/from-template")
                        .header("Authorization", "Bearer " + pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "templateId": "%s",
                                  "planCode": "PLAN-FROM-TPL",
                                  "planName": "Plan From Template SDL",
                                  "planType": "MASTER",
                                  "startDate": "2026-08-10"
                                }
                                """.formatted(projectId, templateId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planCode").value("PLAN-FROM-TPL"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        String planId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        // Fetch tasks to verify 17 tasks created
        mockMvc.perform(get("/api/v1/plans/" + planId + "/tasks")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(17)))
                .andExpect(jsonPath("$[0].wbsCode", is("1")))
                .andExpect(jsonPath("$[16].wbsCode", is("17")));
    }

    @Test
    void testGetPortfolioSummary() throws Exception {
        String pm = login("pm.tpl", PASSWORD);

        mockMvc.perform(get("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProjects", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.projects[0].code").value("PRJ-TPL"));
    }

    // Helper methods
    private Role createRole(String code, String name, String[] permNames) {
        Role role = roleRepository.findByCode(code).orElseGet(() -> {
            Role r = new Role();
            r.setCode(code);
            r.setName(name);
            r.setDescription(name);
            return roleRepository.save(r);
        });
        Set<Permission> perms = role.getPermissions();
        for (String pName : permNames) {
            Permission p = permissionRepository.findByCode(pName).orElseGet(() -> {
                Permission perm = new Permission();
                perm.setCode(pName);
                perm.setName(pName);
                perm.setDescription(pName);
                return permissionRepository.save(perm);
            });
            perms.add(p);
        }
        role.setPermissions(perms);
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
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        return login("admin.tpl", PASSWORD);
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
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
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
