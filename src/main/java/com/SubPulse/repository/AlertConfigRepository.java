package com.subpulse.repository;

import com.subpulse.entity.AlertConfig;
import com.subpulse.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertConfigRepository extends JpaRepository<AlertConfig, Long> {

    /** All enabled alert configs for a given subscription. */
    List<AlertConfig> findBySubscriptionIdAndIsEnabledTrue(Long subscriptionId);

    /** All alert configs for a given subscription (enabled + disabled). */
    List<AlertConfig> findBySubscriptionId(Long subscriptionId);

    /**
     * Check duplicate: same subscription + same channel + same days_before.
     *
     * NOTE: Cannot use derived query method name here because Spring Data JPA
     * treats 'Before' as a date-comparison keyword, causing a parse error on
     * the field name 'daysBefore'. Explicit JPQL bypasses keyword parsing.
     */
    @Query("SELECT COUNT(a) > 0 FROM AlertConfig a " +
           "WHERE a.subscription.id = :subscriptionId " +
           "AND a.channel = :channel " +
           "AND a.daysBefore = :daysBefore")
    boolean existsBySubscriptionIdAndChannelAndDaysBefore(
            @Param("subscriptionId") Long subscriptionId,
            @Param("channel") NotificationChannel channel,
            @Param("daysBefore") Integer daysBefore
    );

    /**
     * Find all enabled alerts for a specific days_before value (used by scheduler).
     *
     * NOTE: Same 'Before' keyword conflict — using explicit JPQL.
     */
    @Query("SELECT a FROM AlertConfig a " +
           "WHERE a.daysBefore = :daysBefore " +
           "AND a.isEnabled = true")
    List<AlertConfig> findByDaysBeforeAndIsEnabledTrue(@Param("daysBefore") Integer daysBefore);
}
