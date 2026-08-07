package com.subpulse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableCaching
@EnableScheduling
@SpringBootApplication
public class SubPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubPulseApplication.class, args);
        log.info("""
                ========================================================
                 🚀  SubPulse started successfully!
                 ✅  Application is up and running.
                 🌐  Swagger UI : http://localhost:8080/swagger-ui.html
                 📖  API Docs   : http://localhost:8080/api-docs
                ========================================================
                """);
    }
}
