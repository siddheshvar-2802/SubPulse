package com.subpulse.scheduler;

import com.subpulse.entity.User;
import com.subpulse.repository.UserRepository;
import com.subpulse.service.MonthlyDigestReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled cron job that automatically compiles and dispatches
 * the Monthly Executive Digest (HTML email + attached PDF report)
 * to all active users on the 1st day of every month at 08:00 AM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyDigestScheduler {

    private final UserRepository              userRepository;
    private final MonthlyDigestReportService  monthlyDigestReportService;

    @Scheduled(cron = "${app.scheduler.monthly-digest-cron:0 0 8 1 * *}")
    @SchedulerLock(
            name = "monthlyDigestTask",
            lockAtLeastFor = "PT5M",
            lockAtMostFor  = "PT30M"
    )
    public void executeMonthlyDigest() {
        log.info("Starting automated Monthly Executive Digest dispatch for active users...");

        List<User> activeUsers = userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()) && u.getEmail() != null)
                .toList();

        int sentCount = 0;
        int failCount = 0;

        for (User user : activeUsers) {
            try {
                monthlyDigestReportService.sendMonthlyDigestEmail(user.getId(), user.getPreferredCurrency());
                sentCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("Could not dispatch monthly digest for user {}: {}", user.getEmail(), e.getMessage());
            }
        }

        log.info("Finished automated Monthly Digest cron: {} sent, {} failed across {} users",
                sentCount, failCount, activeUsers.size());
    }
}
