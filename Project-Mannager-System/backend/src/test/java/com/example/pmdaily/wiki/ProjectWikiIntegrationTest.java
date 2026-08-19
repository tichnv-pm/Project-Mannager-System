package com.example.pmdaily.wiki;

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

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.example.pmdaily.PMDailyApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectWikiIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] PROJECT_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage"};

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

    private UUID pmUserId;
    private UUID memberUserId;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                String[] tables = {
                        "project_wiki_page_histories", "project_wiki_pages", "wiki_page_templates",
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

        // Insert default templates in DB for tests
        jdbcTemplate.execute("INSERT INTO wiki_page_templates (id, parent_template_id, title, content_placeholder, sequence_no, version) VALUES " +
                "('00000000-0000-0000-0000-000000000101', NULL, '1. Hướng dẫn Bắt đầu (Getting Started)', 'Placeholder 1', 1, 0), " +
                "('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000101', '1.1 Local Setup', 'Placeholder 2', 2, 0)");

        Role pmRole = createRole("PROJECT_MANAGER", "PM", PROJECT_PERMS);
        Role memberRole = createRole("PROJECT_MEMBER", "Member", new String[]{"project:view"});

        User pm = createUser("pm.wiki", "pm.wiki@example.com", pmRole);
        User member = createUser("member.wiki", "member.wiki@example.com", memberRole);

        pmUserId = pm.getId();
        memberUserId = member.getId();

        projectId = createProject(pmToken(), "PRJ-WIKI", "Project Wiki Test", pmUserId);
        addMember(pmToken(), projectId, memberUserId, ProjectMemberRole.DEVELOPER);
    }

    @Test
    void testWikiLifecycle_Initialize_CRUD_VersionConflict() throws Exception {
        String pmToken = pmToken();
        String memberToken = memberToken();

        // 1. Initially, Wiki list is empty
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/wiki")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 2. Initialize from template -> should create 2 pages
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/wiki/initialize")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isCreated());

        // 3. Duplicate initialize -> 409 Conflict
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/wiki/initialize")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isConflict());

        // 4. Retrieve list -> returns 2 pages
        MvcResult listRes = mockMvc.perform(get("/api/v1/projects/" + projectId + "/wiki")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("1. Hướng dẫn Bắt đầu (Getting Started)"))
                .andReturn();

        String jsonList = listRes.getResponse().getContentAsString();
        JsonNode listNode = objectMapper.readTree(jsonList);
        String gettingStartedId = listNode.get(0).get("id").asText();

        // 5. Create a new custom sub-page under "Getting Started"
        String customPageJson = """
                {
                    "parentPageId": "%s",
                    "title": "1.2 Deployment Guide",
                    "content": "Deploy using Docker: `docker compose up`"
                }
                """.formatted(gettingStartedId);

        MvcResult createRes = mockMvc.perform(post("/api/v1/projects/" + projectId + "/wiki")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customPageJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.title").value("1.2 Deployment Guide"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        String customPageId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        // 6. Update the custom page -> version increments to 1
        String updateJson = """
                {
                    "title": "1.2 Deployment Guide (Updated)",
                    "content": "Deploy using: `docker compose up -d`",
                    "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/wiki-pages/" + customPageId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.title").value("1.2 Deployment Guide (Updated)"));

        // 7. Update again with stale version (0 instead of 1) -> 409 Conflict
        String staleUpdateJson = """
                {
                    "title": "1.2 Deployment Guide (Stale)",
                    "content": "This should fail",
                    "version": 0
                }
                """;

        mockMvc.perform(put("/api/v1/wiki-pages/" + customPageId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staleUpdateJson))
                .andExpect(status().isConflict());

        // 8. Delete the custom page -> returns 204 No Content
        mockMvc.perform(delete("/api/v1/wiki-pages/" + customPageId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        // 9. Verify deletion: list has size 2 again
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/wiki")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ------------------------------------------------------------------ HELPERS
    private String pmToken() throws Exception {
        return login("pm.wiki", PASSWORD);
    }

    private String memberToken() throws Exception {
        return login("member.wiki", PASSWORD);
    }

    private String login(String username, String password) throws Exception {
        String json = """
                {
                    "username": "%s",
                    "password": "%s"
                }
                """.formatted(username, password);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
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

    private String createProject(String token, String code, String name, UUID pmId) throws Exception {
        String json = """
                {
                    "code": "%s",
                    "name": "%s",
                    "description": "Desc project",
                    "projectManagerId": "%s"
                }
                """.formatted(code, name, pmId);
        MvcResult res = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void addMember(String token, String prjId, UUID userId, ProjectMemberRole role) throws Exception {
        String json = """
                {
                    "userId": "%s",
                    "role": "%s"
                }
                """.formatted(userId, role.name());
        mockMvc.perform(post("/api/v1/projects/" + prjId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    private String[] concat(String[] a, String[] b) {
        String[] c = new String[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}
