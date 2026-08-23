package com.subpulse.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures legacy PostgreSQL check constraints on 'channel' columns
 * are dropped or updated to allow modern channels (TELEGRAM, EMAIL, DISCORD, WEBHOOK).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseConstraintFixRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            log.info("Ensuring PostgreSQL schema and database channel constraints are ready...");
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS subpulse;");
            
            // Drop restrictive legacy enum check constraints created by previous Hibernate versions
            jdbcTemplate.execute("ALTER TABLE IF EXISTS alert_configs DROP CONSTRAINT IF EXISTS alert_configs_channel_check;");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS notification_logs DROP CONSTRAINT IF EXISTS notification_logs_channel_check;");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS subscriptions DROP CONSTRAINT IF EXISTS subscriptions_category_check;");
            
            // Migrate any existing legacy WHATSAPP rows to TELEGRAM
            jdbcTemplate.execute("UPDATE alert_configs SET channel = 'TELEGRAM' WHERE channel = 'WHATSAPP';");
            jdbcTemplate.execute("UPDATE notification_logs SET channel = 'TELEGRAM' WHERE channel = 'WHATSAPP';");
            
            log.info("Database channel constraints upgraded and legacy records migrated successfully (TELEGRAM enabled).");
        } catch (Exception e) {
            log.warn("Database constraint upgrade check: {}", e.getMessage());
        }
    }
}
