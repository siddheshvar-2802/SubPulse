package com.subpulse.repository;

import com.subpulse.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** All active subscriptions for a user. */
    List<Subscription> findByUserIdAndIsActiveTrue(Long userId);

    /** All subscriptions (active + inactive) for a user. */
    List<Subscription> findByUserId(Long userId);

    /**
     * Find subscriptions whose next billing date falls exactly N days from today.
     * Used by the daily renewal alert scheduler.
     */
    List<Subscription> findByNextBillingDateAndIsActiveTrue(LocalDate nextBillingDate);

    /**
     * Find active subscriptions renewing within the next N days.
     * Used for dashboard "upcoming renewals" view.
     */
    @Query("SELECT s FROM Subscription s WHERE s.user.id = :userId " +
           "AND s.isActive = true " +
           "AND s.nextBillingDate BETWEEN :today AND :future " +
           "ORDER BY s.nextBillingDate ASC")
    List<Subscription> findUpcomingRenewals(
            @Param("userId") Long userId,
            @Param("today") LocalDate today,
            @Param("future") LocalDate future
    );

    /**
     * Sum of all active subscription amounts in a given currency for a user.
     * Used for monthly spend analytics.
     */
    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Subscription s " +
           "WHERE s.user.id = :userId AND s.isActive = true AND s.currency = :currency")
    BigDecimal sumActiveAmountByUserAndCurrency(
            @Param("userId") Long userId,
            @Param("currency") String currency
    );

    /** Check if a subscription belongs to a specific user (ownership guard). */
    boolean existsByIdAndUserId(Long id, Long userId);
}
