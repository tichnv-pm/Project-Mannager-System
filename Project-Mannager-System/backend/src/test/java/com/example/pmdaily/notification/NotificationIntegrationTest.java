package com.example.pmdaily.notification;

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
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskPriority;
import com.example.pmdaily.task.TaskRepository;
import com.example.pmdaily.task.TaskStatus;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.user.Permission;
import com.example.pmdaily.user.PermissionRepository;
import com.example.pmdaily.user.Role;
import com.example.pmdaily.user.RoleRepository;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] USER1_PERMS = {"notification:view", "notification:manage"};
    private static final String[] USER2_PERMS = {"notification:view", "notification:manage"};

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
    private NotificationService notificationService;
    @Autowired
    private NotificationScheduler notificationScheduler;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository memberRepository;
    @Autowired
    private TaskRepository taskRepository;

    private User user1;
    private User user2;
    private Project project;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                statement.executeUpdate("DELETE FROM notifications");
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

        Role role1 = createRole("ROLE_USER1", "User 1 Role", USER1_PERMS);
        Role role2 = createRole("ROLE_USER2", "User 2 Role", USER2_PERMS);

        user1 = createUser("user1.nft", "user1.nft@example.com", role1);
        user2 = createUser("user2.nft", "user2.nft@example.com", role2);

        project = new Project();
        project.setCode("PRJ-NFT");
        project.setName("Project Notification Test");
        project.setStartDate(LocalDate.now());
        project.setEndDate(LocalDate.now().plusMonths(3));
        project = projectRepository.save(project);
    }

    @Test
    void testNotificationListUnreadMarkReadAndReadAll() throws Exception {
        // Create 2 internal notifications for user1
        Notification n1 = notificationService.createNotificationInternal(
                user1, NotificationType.TASK_ASSIGNED, "Được giao task mới", "Bạn vừa được giao task T1", "TASK", UUID.randomUUID());
        Notification n2 = notificationService.createNotificationInternal(
                user1, NotificationType.TASK_DUE_SOON, "Task sắp đến hạn", "Task T2 sắp đến hạn", "TASK", UUID.randomUUID());

        // Create 1 internal notification for user2
        notificationService.createNotificationInternal(
                user2, NotificationType.TASK_COMMENTED, "Bình luận mới", "Có comment mới", "TASK", UUID.randomUUID());

        String token1 = login("user1.nft", PASSWORD);
        String token2 = login("user2.nft", PASSWORD);

        // 1. User1 gets unread count -> 2
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));

        // 2. User1 gets notification list -> 2 items
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));

        // 3. User1 marks N1 as read
        mockMvc.perform(put("/api/v1/notifications/" + n1.getId() + "/read")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        // Check unread count is now 1
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        // 4. User2 tries to mark N2 (user1's notification) as read -> 404 NOT_FOUND
        mockMvc.perform(put("/api/v1/notifications/" + n2.getId() + "/read")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());

        // 5. User1 marks all as read
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1));

        // Check unread count is now 0
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void testScheduledJobDeduplication() throws Exception {
        // Create an overdue task for user1
        Task overdueTask = new Task();
        overdueTask.setCode("PRJ-NFT-TASK-001");
        overdueTask.setTitle("Fix critical security bug");
        overdueTask.setProject(project);
        overdueTask.setAssignee(user1);
        overdueTask.setReporter(user1);
        overdueTask.setPriority(TaskPriority.HIGH);
        overdueTask.setStatus(TaskStatus.IN_PROGRESS);
        overdueTask.setDueDate(LocalDate.now().minusDays(2));
        taskRepository.save(overdueTask);

        // Trigger scheduler 1st time
        notificationScheduler.scanNotifications();

        String token1 = login("user1.nft", PASSWORD);
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        // Trigger scheduler 2nd time on same day -> deduplicated, unread count stays 1
        notificationScheduler.scanNotifications();

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
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
