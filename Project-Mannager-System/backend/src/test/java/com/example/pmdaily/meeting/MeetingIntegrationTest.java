package com.example.pmdaily.meeting;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.user.Permission;
import com.example.pmdaily.user.PermissionRepository;
import com.example.pmdaily.user.Role;
import com.example.pmdaily.user.RoleRepository;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test Meeting module (docs/api/06-meeting-api.md, FR-MEET-01..07, BR-MEET-01..06).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeetingIntegrationTest {

    private static final String PASSWORD = "Abc@12345";
    private static final String[] ADMIN_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage",
                    "meeting:view", "meeting:manage"};
    private static final String[] PM_PERMS =
            {"project:view", "project:create", "project:update", "project:delete", "project-member:manage",
                    "meeting:view", "meeting:manage"};
    private static final String[] MEMBER_PERMS = {"meeting:view"};

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
    private ProjectMemberRepository memberRepository;
    @Autowired
    private MeetingRepository meetingRepository;

    private UUID adminId;
    private UUID pmUserId;
    private UUID chairId;
    private UUID memberId;
    private UUID outsiderId;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (java.sql.Statement statement = con.createStatement()) {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                statement.executeUpdate("DELETE FROM meeting_participants");
                statement.executeUpdate("DELETE FROM attachments");
                statement.executeUpdate("DELETE FROM meetings");
                statement.executeUpdate("DELETE FROM project_sequences");
                statement.executeUpdate("DELETE FROM project_members");
                statement.executeUpdate("DELETE FROM projects");
                statement.executeUpdate("DELETE FROM user_roles");
                statement.executeUpdate("DELETE FROM role_permissions");
                statement.executeUpdate("DELETE FROM refresh_tokens");
                statement.executeUpdate("DELETE FROM audit_logs");
                statement.executeUpdate("DELETE FROM users");
                statement.executeUpdate("DELETE FROM roles");
                statement.executeUpdate("DELETE FROM permissions");
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            return null;
        });

        adminId = createUser("admin.mtg", "admin.mtg@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "ADMIN", ADMIN_PERMS);
        pmUserId = createUser("pm.mtg", "pm.mtg@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "PROJECT_MANAGER", PM_PERMS);
        chairId = createUser("chair.mtg", "chair.mtg@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "MEMBER", MEMBER_PERMS);
        memberId = createUser("member.mtg", "member.mtg@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "MEMBER", MEMBER_PERMS);
        outsiderId = createUser("outsider.mtg", "outsider.mtg@pmdaily.local", PASSWORD, UserStatus.ACTIVE, "MEMBER", MEMBER_PERMS);

        projectId = createProject("PRJ200", pmUserId);
        addMember(projectId, chairId, ProjectMemberRole.DEVELOPER);
        addMember(projectId, memberId, ProjectMemberRole.DEVELOPER);
    }

    private UUID createUser(String username, String email, String password, UserStatus status,
            String roleCode, String... permissionCodes) {
        Set<Permission> permissions = new java.util.HashSet<>();
        for (String code : permissionCodes) {
            Permission permission = permissionRepository.findByCode(code).orElseGet(() -> {
                Permission created = new Permission();
                created.setCode(code);
                created.setName(code);
                return permissionRepository.save(created);
            });
            permissions.add(permission);
        }

        Role role = new Role();
        role.setCode("MEMBER".equals(roleCode) ? "MEMBER_" + username : roleCode);
        role.setName(roleCode + " " + username);
        role.setPermissions(permissions);
        roleRepository.save(role);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(status);
        user.getRoles().add(role);
        userRepository.save(user);

        return user.getId();
    }

    private String createProject(String code, UUID managerId) throws Exception {
        String body = managerId != null
                ? """
                        {"code":"%s","name":"Dự án %s","description":"desc","status":"ACTIVE",
                         "startDate":"2026-05-01","endDate":"2026-11-30","projectManagerId":"%s"}
                        """.formatted(code, code, managerId)
                : """
                        {"code":"%s","name":"Dự án %s","description":"desc","status":"PLANNING"}
                        """.formatted(code, code);
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void addMember(String projectId, UUID userId, ProjectMemberRole role) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
                        .header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"%s"}
                                """.formatted(userId, role)))
                .andExpect(status().isCreated());
    }

    private MvcResult loginAs(String username) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult admin() throws Exception {
        return loginAs("admin.mtg");
    }

    private MvcResult pm() throws Exception {
        return loginAs("pm.mtg");
    }

    private MvcResult chair() throws Exception {
        return loginAs("chair.mtg");
    }

    private MvcResult member() throws Exception {
        return loginAs("member.mtg");
    }

    private MvcResult outsider() throws Exception {
        return loginAs("outsider.mtg");
    }

    private String bearer(MvcResult result) throws Exception {
        return "Bearer " + tokenFrom(result, "accessToken");
    }

    private String tokenFrom(MvcResult result, String field) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get(field).asText();
    }

    private String meetingBody(String title, String startTime, String endTime, String chairpersonId,
            String participantJson, String location, String meetingLink, String status) {
        return """
                {"projectId":"%s","title":"%s","startTime":"%s","endTime":"%s","chairpersonId":"%s",
                 "participantIds":%s,"location":"%s","meetingLink":"%s"%s}
                """.formatted(projectId, title, startTime, endTime, chairpersonId,
                participantJson, location == null ? "" : location, meetingLink == null ? "" : meetingLink,
                status == null ? "" : ",\"status\":\"" + status + "\"");
    }

    private String createMeeting(MvcResult actor, String title, String startTime, String endTime,
            String chairpersonId, String participantJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody(title, startTime, endTime, chairpersonId,
                                participantJson, "Phòng họp 2", null, null)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // ------------------------------------------------------------------ CRUD

    @Test
    void create_success_returns201WithDefaultStatusAndParticipants() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody("Họp sprint 12", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                                pmUserId.toString(), "[\"%s\"]".formatted(memberId),
                                "Phòng họp 2", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Họp sprint 12"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.location").value("Phòng họp 2"))
                .andExpect(jsonPath("$.projectCode").value("PRJ200"))
                .andExpect(jsonPath("$.projectName").isNotEmpty())
                .andExpect(jsonPath("$.chairperson.fullName").value("pm.mtg"))
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andExpect(jsonPath("$.participants[0].fullName").value("member.mtg"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertNotNull(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    @Test
    void create_endTimeNotAfterStart_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody("Họp sai giờ", "2026-08-05T03:00:00Z", "2026-08-05T02:00:00Z",
                                pmUserId.toString(), "[]", "Phòng họp 2", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void create_chairpersonOutsideProject_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody("Họp sai chủ trì", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                                outsiderId.toString(), "[]", "Phòng họp 2", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    void create_participantOutsideProject_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody("Họp sai participant", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                                pmUserId.toString(), "[\"%s\"]".formatted(outsiderId),
                                "Phòng họp 2", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    void create_duplicateParticipants_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody("Họp trùng participant", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                                pmUserId.toString(),
                                "[\"%s\",\"%s\"]".formatted(memberId, memberId),
                                "Phòng họp 2", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void create_noLocationNoLink_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody("Họp không địa điểm", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                                pmUserId.toString(), "[]", null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void create_byMember_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", bearer(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody("Họp không phép", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                                pmUserId.toString(), "[]", "Phòng họp 2", null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_success_returnsFullDetail() throws Exception {
        String meetingId = createMeeting(pm(), "Họp chi tiết", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[\"%s\"]".formatted(memberId));
        mockMvc.perform(get("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Họp chi tiết"))
                .andExpect(jsonPath("$.attachments.length()").value(0));
    }

    @Test
    void get_byOutsider_returns403() throws Exception {
        String meetingId = createMeeting(pm(), "Họp bí mật", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(get("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(outsider())))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/meetings/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_filtersByStatusAndKeyword() throws Exception {
        createMeeting(pm(), "Họp sprint 20", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        createMeeting(pm(), "Họp sprint 21", "2026-08-06T02:00:00Z", "2026-08-06T03:00:00Z",
                pmUserId.toString(), "[]");

        mockMvc.perform(get("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .param("keyword", "sprint 21")
                        .param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Họp sprint 21"));

        mockMvc.perform(get("/api/v1/meetings")
                        .header("Authorization", bearer(outsider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void list_fromAfterTo_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .param("fromTime", "2026-08-06T02:00:00Z")
                        .param("toTime", "2026-08-05T02:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void list_invalidSort_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .param("sort", "startDate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void today_returnsOnlyTodayNonCancelled() throws Exception {
        String today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).plusHours(10).format(DateTimeFormatter.ISO_INSTANT);
        String tomorrow = LocalDate.now().atStartOfDay(ZoneOffset.UTC).plusDays(1).plusHours(10)
                .format(DateTimeFormatter.ISO_INSTANT);
        createMeeting(pm(), "Họp hôm nay", today, LocalDate.now().atStartOfDay(ZoneOffset.UTC).plusHours(11)
                .format(DateTimeFormatter.ISO_INSTANT), pmUserId.toString(), "[]");
        createMeeting(pm(), "Họp ngày mai", tomorrow, LocalDate.now().atStartOfDay(ZoneOffset.UTC).plusDays(1)
                .plusHours(11).format(DateTimeFormatter.ISO_INSTANT), pmUserId.toString(), "[]");
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody("Họp hôm nay đã hủy",
                                today, LocalDate.now().atStartOfDay(ZoneOffset.UTC).plusHours(11)
                                        .format(DateTimeFormatter.ISO_INSTANT),
                                pmUserId.toString(), "[]", "Phòng họp 2", null, "CANCELLED")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/meetings/today")
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Họp hôm nay"));
    }

    @Test
    void update_success_returns200() throws Exception {
        String meetingId = createMeeting(pm(), "Họp cũ", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[\"%s\"]".formatted(memberId));
        String body = """
                {"projectId":"%s","title":"Họp mới","startTime":"2026-08-06T02:00:00Z","endTime":"2026-08-06T03:00:00Z",
                 "location":"Phòng họp 1","chairpersonId":"%s","participantIds":["%s"],"version":0}
                """.formatted(projectId, pmUserId, chairId);
        mockMvc.perform(put("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Họp mới"))
                .andExpect(jsonPath("$.location").value("Phòng họp 1"))
                .andExpect(jsonPath("$.participants[0].fullName").value("chair.mtg"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void update_staleVersion_returns409() throws Exception {
        String meetingId = createMeeting(pm(), "Họp version", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        String body = """
                {"projectId":"%s","title":"Họp version 2","startTime":"2026-08-05T02:00:00Z","endTime":"2026-08-05T03:00:00Z",
                 "location":"Phòng họp 2","chairpersonId":"%s","version":9}
                """.formatted(projectId, pmUserId);
        mockMvc.perform(put("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void update_cancelledToOtherStatus_returns400() throws Exception {
        String meetingId = createMeeting(pm(), "Họp bị hủy", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(put("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Họp bị hủy","startTime":"2026-08-05T02:00:00Z","endTime":"2026-08-05T03:00:00Z",
                                 "location":"Phòng họp 2","chairpersonId":"%s","status":"CANCELLED","version":0}
                                """.formatted(projectId, pmUserId)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Họp bị hủy","startTime":"2026-08-05T02:00:00Z","endTime":"2026-08-05T03:00:00Z",
                                 "location":"Phòng họp 2","chairpersonId":"%s","status":"IN_PROGRESS","version":1}
                                """.formatted(projectId, pmUserId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void complete_byChairpersonOnly_success() throws Exception {
        String meetingId = createMeeting(pm(), "Họp hoàn thành", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                chairId.toString(), "[]");
        mockMvc.perform(put("/api/v1/meetings/{id}/complete", meetingId)
                        .header("Authorization", bearer(chair()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Nội dung diễn biến","conclusion":"Kết luận cuộc họp"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.conclusion").value("Kết luận cuộc họp"))
                .andExpect(jsonPath("$.content").value("Nội dung diễn biến"));
    }

    @Test
    void complete_byNonChairMember_returns403() throws Exception {
        String meetingId = createMeeting(pm(), "Họp không phép", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(put("/api/v1/meetings/{id}/complete", meetingId)
                        .header("Authorization", bearer(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conclusion":"Kết luận"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void complete_missingConclusion_returns400() throws Exception {
        String meetingId = createMeeting(pm(), "Họp thiếu kết luận", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(put("/api/v1/meetings/{id}/complete", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Nội dung"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void complete_cancelledMeeting_returns400() throws Exception {
        String meetingId = createMeeting(pm(), "Họp hủy rồi", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(put("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Họp hủy rồi","startTime":"2026-08-05T02:00:00Z","endTime":"2026-08-05T03:00:00Z",
                                 "location":"Phòng họp 2","chairpersonId":"%s","status":"CANCELLED","version":0}
                                """.formatted(projectId, pmUserId)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/meetings/{id}/complete", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conclusion":"Kết luận"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void participants_addRemove_success() throws Exception {
        String meetingId = createMeeting(pm(), "Họp participants", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(put("/api/v1/meetings/{id}/participants", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"add":["%s","%s"],"remove":[]}
                                """.formatted(memberId, chairId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(2));
        mockMvc.perform(put("/api/v1/meetings/{id}/participants", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"add":[],"remove":["%s"]}
                                """.formatted(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(1));
    }

    @Test
    void participants_addDuplicate_returns400() throws Exception {
        String meetingId = createMeeting(pm(), "Họp trùng", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(put("/api/v1/meetings/{id}/participants", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"add":["%s","%s"],"remove":[]}
                                """.formatted(memberId, memberId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void participants_addOutsideProject_returns400() throws Exception {
        String meetingId = createMeeting(pm(), "Họp lạ", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(put("/api/v1/meetings/{id}/participants", meetingId)
                        .header("Authorization", bearer(pm()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"add":["%s"],"remove":[]}
                                """.formatted(outsiderId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    void delete_success_returns204ThenNotFound() throws Exception {
        String meetingId = createMeeting(pm(), "Họp xóa", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(delete("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(admin())))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_byMember_returns403() throws Exception {
        String meetingId = createMeeting(pm(), "Họp giữ", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(delete("/api/v1/meetings/{id}", meetingId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ attachments

    @Test
    void attachment_uploadListDownloadDelete_success() throws Exception {
        String meetingId = createMeeting(pm(), "Họp biên bản", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");

        MvcResult upload = mockMvc.perform(multipart("/api/v1/meetings/{id}/attachments", meetingId)
                        .file(new MockMultipartFile("file", "bieu-ban.txt", "text/plain",
                                "nội dung test".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("bieu-ban.txt"))
                .andExpect(jsonPath("$.sizeBytes").value(15))
                .andExpect(jsonPath("$.uploadedBy.fullName").value("pm.mtg"))
                .andReturn();
        String attachmentId = objectMapper.readTree(upload.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/meetings/{id}/attachments", meetingId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(attachmentId));

        mockMvc.perform(get("/api/v1/meetings/{id}/attachments/{aid}/download", meetingId, attachmentId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().bytes("nội dung test".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        mockMvc.perform(delete("/api/v1/meetings/{id}/attachments/{aid}", meetingId, attachmentId)
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/meetings/{id}/attachments", meetingId)
                        .header("Authorization", bearer(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void attachment_oversize_returns413() throws Exception {
        String meetingId = createMeeting(pm(), "Họp file to", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        mockMvc.perform(multipart("/api/v1/meetings/{id}/attachments", meetingId)
                        .file(new MockMultipartFile("file", "to.txt", "text/plain", big))
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void attachment_wrongMime_returns400() throws Exception {
        String meetingId = createMeeting(pm(), "Họp file lạ", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(multipart("/api/v1/meetings/{id}/attachments", meetingId)
                        .file(new MockMultipartFile("file", "virus.exe", "application/x-msdownload",
                                "bad".getBytes()))
                        .header("Authorization", bearer(pm())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void attachment_uploadByMember_returns403() throws Exception {
        String meetingId = createMeeting(pm(), "Họp file cấm", "2026-08-05T02:00:00Z", "2026-08-05T03:00:00Z",
                pmUserId.toString(), "[]");
        mockMvc.perform(multipart("/api/v1/meetings/{id}/attachments", meetingId)
                        .file(new MockMultipartFile("file", "bieu-ban.txt", "text/plain",
                                "nội dung".getBytes()))
                        .header("Authorization", bearer(member())))
                .andExpect(status().isForbidden());
    }
}
