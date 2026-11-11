package com.example.pmdaily.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit mặc định bị tắt ở profile test (application-test.yml) để không phá các test khác
 * gọi login/webhook nhiều lần; bật lại riêng cho test này qua @TestPropertySource.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.login.max-requests=3",
        "app.security.rate-limit.login.window-seconds=60",
        "app.security.rate-limit.webhook.max-requests=2",
        "app.security.rate-limit.webhook.window-seconds=60"
})
class RateLimitFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String LOGIN_BODY = """
            {"username":"no-such-user-ratelimit","password":"wrong-password"}
            """;

    @Test
    void login_overLimit_returns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_BODY))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void webhook_overLimit_returns429() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/public/webhooks/git")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/public/webhooks/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
