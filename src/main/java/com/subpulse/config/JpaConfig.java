package com.subpulse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA Auditing so that @CreatedDate and @LastModifiedDate
 * in BaseEntity are automatically populated on every save.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
