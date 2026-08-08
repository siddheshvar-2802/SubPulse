package com.subpulse.repository;

import com.subpulse.entity.NotificationLog;
import com.subpulse.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /** Paginated log history for a subscription (newest first). */
    Page<NotificationLog> findBySubscriptionIdOrderBySentAtDesc(Long subscriptionId, Pageable pageable);

    /** All logs for a subscription with a specific status (e.g., FAILED). */
    List<NotificationLog> findBySubscriptionIdAndStatus(Long subscriptionId, NotificationStatus status);

    /**
     * Check if a notification for this subscription was already sent
     * for a given daysRemaining value. Prevents duplicate alerts on retries.
     */
    boolean existsBySubscriptionIdAndDaysRemainingAndStatus(
            Long subscriptionId,
            Integer daysRemaining,
            NotificationStatus status
    );
}
