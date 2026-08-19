package com.example.pmdaily.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật scheduling — dùng cho NotificationScheduler (docs/design/02 muc 10).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
