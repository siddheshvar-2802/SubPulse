package com.example.SubPulse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class SubPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubPulseApplication.class, args);
        System.out.println("""
                ========================================================
                🚀 SubPulse started successfully!
                ✅ Application is up and running.
                🌐 Ready to accept requests.
                ========================================================
                """);
    }

}
