package com.example.pmdaily.git;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/public/webhooks")
public class GitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitWebhookController.class);

    private final GitIntegrationService gitIntegrationService;
    private final ObjectMapper objectMapper;

    @Value("${app.webhook.git-secret:git-webhook-secret-key-12345}")
    private String gitWebhookSecret;

    public GitWebhookController(GitIntegrationService gitIntegrationService, ObjectMapper objectMapper) {
        this.gitIntegrationService = gitIntegrationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/git")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String githubSignature,
            @RequestHeader(value = "X-Gitlab-Token", required = false) String gitlabToken,
            @RequestBody String payload) {

        log.info("Received git webhook request. GitHub Sig: {}, GitLab Token: {}", githubSignature != null, gitlabToken != null);

        // Security Validation
        boolean authenticated = false;
        if (githubSignature != null) {
            authenticated = verifyGitHubSignature(githubSignature, payload, gitWebhookSecret);
        } else if (gitlabToken != null) {
            authenticated = verifyGitLabToken(gitlabToken, gitWebhookSecret);
        }

        if (!authenticated) {
            log.warn("Git webhook signature verification failed");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Chữ ký webhook không hợp lệ");
        }

        // Parse Payload
        try {
            JsonNode root = objectMapper.readTree(payload);

            if (root.has("commits")) {
                // Push Event (Git commits)
                JsonNode commitsNode = root.get("commits");
                if (commitsNode.isArray()) {
                    for (JsonNode commitNode : commitsNode) {
                        String hash = commitNode.path("id").asText();
                        if (hash.isEmpty()) {
                            hash = commitNode.path("sha").asText();
                        }
                        String message = commitNode.path("message").asText();
                        String author = commitNode.path("author").path("name").asText();
                        if (author.isEmpty()) {
                            author = commitNode.path("author").path("username").asText();
                        }
                        String url = commitNode.path("url").asText();

                        gitIntegrationService.processCommit(hash, message, author, url);
                    }
                }
            } else if (root.has("pull_request")) {
                // GitHub Pull Request Event
                String action = root.path("action").asText();
                JsonNode prNode = root.get("pull_request");
                int number = prNode.path("number").asInt();
                String title = prNode.path("title").asText();
                String state = prNode.path("state").asText(); // "open", "closed"
                boolean merged = prNode.path("merged").asBoolean();
                String url = prNode.path("html_url").asText();

                gitIntegrationService.processGitHubPullRequest(number, title, state, merged, url);
            } else if ("merge_request".equals(root.path("object_kind").asText())) {
                // GitLab Merge Request Event
                JsonNode attrs = root.get("object_attributes");
                int number = attrs.path("iid").asInt();
                String title = attrs.path("title").asText();
                String state = attrs.path("state").asText(); // "opened", "merged", "closed"
                String url = attrs.path("url").asText();
                boolean merged = "merged".equalsIgnoreCase(state);

                gitIntegrationService.processGitLabMergeRequest(number, title, state, merged, url);
            }

        } catch (Exception e) {
            log.error("Failed to parse git webhook payload", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Payload không hợp lệ");
        }

        return ResponseEntity.ok().build();
    }

    private boolean verifyGitHubSignature(String signatureHeader, String payload, String secret) {
        if (!signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String receivedSig = signatureHeader.substring(7);
        try {
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
            String calculatedSig = hexString.toString();
            return MessageDigest.isEqual(calculatedSig.getBytes(StandardCharsets.UTF_8), receivedSig.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyGitLabToken(String tokenHeader, String secret) {
        if (tokenHeader == null) {
            return false;
        }
        return MessageDigest.isEqual(tokenHeader.trim().getBytes(StandardCharsets.UTF_8), secret.trim().getBytes(StandardCharsets.UTF_8));
    }
}
