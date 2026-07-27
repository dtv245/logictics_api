package com.company.logicstic.modules.notification.event;

import java.util.UUID;

/**
 * Requests a notification visible to every user in the current tenant database.
 *
 * <p>The notification schema has no recipient column. The tenant boundary is therefore the database
 * selected for the current request, rather than an individual employee.
 */
public record TenantNotificationEvent(
    String title, String message, String sourceType, UUID sourceId) {}
