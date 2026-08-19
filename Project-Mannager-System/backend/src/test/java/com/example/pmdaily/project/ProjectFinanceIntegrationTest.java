package com.example.pmdaily.project;

import java.time.LocalDate;
import java.util.List;
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

import com.example.pmdaily.plan.*;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.example.pmdaily.PMDailyApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectFinanceIntegrationTest {

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
    private ProjectPlanRepository planRepository;
    @Autowired
    private PlanTaskRepository planTaskRepository;
    @Autowired
    private ProjectFinancialSnapshotRepository snapshotRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User pm;
    private User dev;
    private User guest;
    private Project project;
    private ProjectMember devMember;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        snapshotRepository.deleteAll();
        planTaskRepository.deleteAll();
        planRepository.deleteAll();
        memberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");

        // Permissions
        Permission viewPerm = new Permission();
        viewPerm.setCode("financial:view");
        viewPerm.setName("View Finance");
        permissionRepository.save(viewPerm);

        Permission updatePerm = new Permission();
        updatePerm.setCode("financial:update");
        updatePerm.setName("Update Finance");
        permissionRepository.save(updatePerm);

        // Roles
        Role pmRole = new Role();
        pmRole.setCode("PROJECT_MANAGER");
        pmRole.setName("Project Manager");
        pmRole.setPermissions(java.util.Set.of(viewPerm, updatePerm));
        roleRepository.save(pmRole);

        Role guestRole = new Role();
        guestRole.setCode("GUEST");
        guestRole.setName("Guest");
        guestRole.setPermissions(java.util.Set.of());
        roleRepository.save(guestRole);

        // Users
        pm = new User();
        pm.setUsername("pm.finance");
        pm.setPasswordHash(passwordEncoder.encode(PASSWORD));
        pm.setFullName("PM Finance");
        pm.setEmail("pm.finance@example.com");
        pm.setStatus(UserStatus.ACTIVE);
        pm.setRoles(java.util.Set.of(pmRole));
        pm = userRepository.save(pm);

        dev = new User();
        dev.setUsername("dev.finance");
        dev.setPasswordHash(passwordEncoder.encode(PASSWORD));
        dev.setFullName("Dev Finance");
        dev.setEmail("dev.finance@example.com");
        dev.setStatus(UserStatus.ACTIVE);
        dev = userRepository.save(dev);

        guest = new User();
        guest.setUsername("guest.finance");
        guest.setPasswordHash(passwordEncoder.encode(PASSWORD));
        guest.setFullName("Guest Finance");
        guest.setEmail("guest.finance@example.com");
        guest.setStatus(UserStatus.ACTIVE);
        guest.setRoles(java.util.Set.of(guestRole));
        guest = userRepository.save(guest);

        // Project
        project = new Project();
        project.setCode("FIN");
        project.setName("Finance Project");
        project.setStatus(ProjectStatus.ACTIVE);
        project.setCreatedBy(pm.getId());
        project.setUpdatedBy(pm.getId());
        project = projectRepository.save(project);

        // Members
        ProjectMember pmMember = new ProjectMember();
        pmMember.setProject(project);
        pmMember.setUser(pm);
        pmMember.setRole(ProjectMemberRole.PROJECT_MANAGER);
        pmMember.setCreatedBy(pm.getId());
        pmMember.setUpdatedBy(pm.getId());
        memberRepository.save(pmMember);

        devMember = new ProjectMember();
        devMember.setProject(project);
        devMember.setUser(dev);
        devMember.setRole(ProjectMemberRole.DEVELOPER);
        devMember.setCreatedBy(pm.getId());
        devMember.setUpdatedBy(pm.getId());
        devMember = memberRepository.save(devMember);
    }

    @Test
    void testSetAndGetEncryptedHourlyRate() throws Exception {
        String pmToken = login("pm.finance", PASSWORD);

        // Set developer hourly rate to 100.0
        mockMvc.perform(put("/api/v1/projects/" + project.getId() + "/finance/members/" + devMember.getId() + "/rate")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hourlyRate\": 100.0}"))
                .andExpect(status().isOk());

        // Verify that the rate in the database is raw encrypted text
        String rateInDb = jdbcTemplate.queryForObject(
                "SELECT hourly_rate FROM project_members WHERE id = ?",
                String.class,
                devMember.getId()
        );
        assertNotNull(rateInDb);
        assertNotEquals("100.0", rateInDb);
        assertNotEquals("100", rateInDb);

        // Read through API, verify it is decrypted correctly to 100.0
        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/finance/members")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].hourlyRate", is(100.0)));
    }

    @Test
    void testRecalculateAndVerifyEvmSnapshot() throws Exception {
        String pmToken = login("pm.finance", PASSWORD);

        // 1. Set member hourly rate
        devMember.setHourlyRate(100.0);
        memberRepository.save(devMember);

        // 2. Create ACTIVE Project Plan
        ProjectPlan plan = new ProjectPlan();
        plan.setProject(project);
        plan.setPlanCode("FIN-PLAN-1");
        plan.setPlanName("EVM Test Plan");
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setPlanType(PlanType.DETAIL);
        plan = planRepository.save(plan);

        // 3. Create Leaf Tasks
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Task 1: 120 mins (2h), assignee = dev, progress = 50%, actual effort = 60 mins (1h)
        PlanTask task1 = new PlanTask();
        task1.setPlan(plan);
        task1.setWbsCode("1.1");
        task1.setTaskCode("T1");
        task1.setTaskName("Task One");
        task1.setTaskType(PlanTaskType.TASK);
        task1.setPlannedStart(yesterday);
        task1.setPlannedFinish(tomorrow);
        task1.setPlannedEffortMinutes(120);
        task1.setActualEffortMinutes(60);
        task1.setPercentComplete(50);
        task1.setOwner(dev);
        planTaskRepository.save(task1);

        // Task 2: 240 mins (4h), assignee = dev, progress = 25%, actual effort = 120 mins (2h)
        PlanTask task2 = new PlanTask();
        task2.setPlan(plan);
        task2.setWbsCode("1.2");
        task2.setTaskCode("T2");
        task2.setTaskName("Task Two");
        task2.setTaskType(PlanTaskType.TASK);
        task2.setPlannedStart(yesterday);
        task2.setPlannedFinish(tomorrow);
        task2.setPlannedEffortMinutes(240);
        task2.setActualEffortMinutes(120);
        task2.setPercentComplete(25);
        task2.setOwner(dev);
        planTaskRepository.save(task2);

        // 4. Trigger EVM recalculation
        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/finance/recalculate")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk());

        // 5. Query EVM snapshots and verify values
        // Math verification:
        // Yesterday to tomorrow is 2 days interval.
        // Yesterday to today is 1 day. ProgressRatio = 1 / 2 = 0.5.
        // Task 1: BAC = 2h * 100 = 200. PV = 200 * 0.5 = 100. EV = 200 * 0.50 = 100. AC = 1h * 100 = 100.
        // Task 2: BAC = 4h * 100 = 400. PV = 400 * 0.5 = 200. EV = 400 * 0.25 = 100. AC = 2h * 100 = 200.
        // Totals: PV = 300.0, EV = 200.0, AC = 300.0. CV = -100.0. SV = -100.0. CPI = 200/300 = 0.67. SPI = 200/300 = 0.67.
        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/finance/evm")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].plannedValue", is(300.0)))
                .andExpect(jsonPath("$[0].earnedValue", is(200.0)))
                .andExpect(jsonPath("$[0].actualCost", is(300.0)))
                .andExpect(jsonPath("$[0].costVariance", is(-100.0)))
                .andExpect(jsonPath("$[0].scheduleVariance", is(-100.0)))
                .andExpect(jsonPath("$[0].cpi", is(200.0 / 300.0)))
                .andExpect(jsonPath("$[0].spi", is(200.0 / 300.0)));
    }

    @Test
    void testAccessDeniedForGuestsWithoutPermission() throws Exception {
        String guestToken = login("guest.finance", PASSWORD);

        // Guest querying EVM snapshots should get 403
        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/finance/evm")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());
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
}
