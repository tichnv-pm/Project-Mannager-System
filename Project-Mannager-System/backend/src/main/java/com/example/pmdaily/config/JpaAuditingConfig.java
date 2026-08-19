package com.example.pmdaily.config;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.pmdaily.security.UserPrincipal;

/**
 * JPA auditing: tự điền createdAt/createdBy/updatedAt/updatedBy (docs/design/02 muc 3).
 * Actor lấy từ UserPrincipal trong SecurityContext; null khi anonymous/system.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
                return Optional.empty();
            }
            return Optional.ofNullable(principal.getId());
        };
    }
}
