package com.subpulse.notification;

import com.subpulse.entity.Subscription;
import com.subpulse.entity.User;
import com.subpulse.enums.NotificationChannel;

/**
 * Strategy interface for notification providers.
 * Each channel (EMAIL, TELEGRAM, DISCORD, WEBHOOK) implements this interface.
 *
 * New channels can be added without modifying existing code — Open/Closed Principle.
 */
public interface NotificationProvider {

    /** Returns the channel this provider handles. */
    NotificationChannel getChannel();

    /**
     * Send a renewal alert.
     *
     * @param user           The user to notify.
     * @param subscription   The subscription about to renew.
     * @param daysRemaining  Days remaining until the renewal date.
     * @param destination    Optional override destination (Telegram chat ID, webhook URL etc.)
     */
    void sendAlert(User user, Subscription subscription, int daysRemaining, String destination);
}
