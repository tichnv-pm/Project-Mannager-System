package com.example.pmdaily.dod;

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
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.user.Permission;
import com.example.pmdaily.user.PermissionRepository;
import com.example.pmdaily.user.Role;
import com.example.pmdaily.user.RoleRepository;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;
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
class ProjectDodItemIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private UUID projectId;
    private String pmToken;
    private String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                String[] tables = {
                        "refresh_tokens", "project_dod_items", "project_members", "projects",
                        "user_roles", "role_permissions", "permissions", "roles", "users"
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

        Permission viewPerm = new Permission();
        viewPerm.setCode("project:view");
        viewPerm.setName("View project");
        permissionRepository.save(viewPerm);

        Permission updatePerm = new Permission();
        updatePerm.setCode("project:update");
        updatePerm.setName("Update project");
        permissionRepository.save(updatePerm);

        Role pmRole = new Role();
        pmRole.setCode("PROJECT_MANAGER");
        pmRole.setName("Project Manager");
        pmRole.setPermissions(Set.of(viewPerm, updatePerm));
        roleRepository.save(pmRole);

        Role memberRole = new Role();
        memberRole.setCode("PROJECT_MEMBER");
        memberRole.setName("Member");
        memberRole.setPermissions(Set.of(viewPerm));
        roleRepository.save(memberRole);

        User pm = new User();
        pm.setUsername("pm.dod");
        pm.setPasswordHash(passwordEncoder.encode(PASSWORD));
        pm.setFullName("PM Dod");
        pm.setEmail("pm.dod@example.com");
        pm.setStatus(UserStatus.ACTIVE);
        pm.setRoles(Set.of(pmRole));
        pm = userRepository.save(pm);

        User member = new User();
        member.setUsername("member.dod");
        member.setPasswordHash(passwordEncoder.encode(PASSWORD));
        member.setFullName("Member Dod");
        member.setEmail("member.dod@example.com");
        member.setStatus(UserStatus.ACTIVE);
        member.setRoles(Set.of(memberRole));
        member = userRepository.save(member);

        Project project = new Project();
        project.setCode("PRJDOD");
        project.setName("DoD Project");
        project.setCreatedBy(pm.getId());
        project.setUpdatedBy(pm.getId());
        project = projectRepository.save(project);
        projectId = project.getId();

        ProjectMember pmMember = new ProjectMember();
        pmMember.setProject(project);
        pmMember.setUser(pm);
        pmMember.setRole(ProjectMemberRole.PROJECT_MANAGER);
        pmMember.setCreatedBy(pm.getId());
        pmMember.setUpdatedBy(pm.getId());
        memberRepository.save(pmMember);

        ProjectMember regularMember = new ProjectMember();
        regularMember.setProject(project);
        regularMember.setUser(member);
        regularMember.setRole(ProjectMemberRole.DEVELOPER);
        regularMember.setCreatedBy(pm.getId());
        regularMember.setUpdatedBy(pm.getId());
        memberRepository.save(regularMember);

        pmToken = login("pm.dod");
        memberToken = login("member.dod");
    }

    @Test
    void create_byPm_success() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/dod-items")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Code đã được review"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Code đã được review"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.displayOrder").value(0));
    }

    @Test
    void create_byMember_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/dod-items")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Không có quyền"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_ordersByDisplayOrder() throws Exception {
        createItem("Item A");
        createItem("Item B");

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/dod-items")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].text").value("Item A"))
                .andExpect(jsonPath("$[1].text").value("Item B"));
    }

    @Test
    void update_deactivate_success() throws Exception {
        MvcResult created = createItem("Sẽ bị tắt");
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/v1/projects/" + projectId + "/dod-items/" + id)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Sẽ bị tắt","active":false,"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void update_staleVersion_returns409() throws Exception {
        MvcResult created = createItem("Item xung đột");
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/v1/projects/" + projectId + "/dod-items/" + id)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Item xung đột","active":true,"version":99}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_byPm_success() throws Exception {
        MvcResult created = createItem("Sẽ bị xóa");
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/dod-items/" + id)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/dod-items")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private MvcResult createItem(String text) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + projectId + "/dod-items")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"%s"}
                                """.formatted(text)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String login(String username) throws Exception {
        String json = objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", PASSWORD));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
