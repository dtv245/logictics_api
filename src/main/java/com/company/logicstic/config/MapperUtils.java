package com.company.logicstic.config;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Utility class for MapStruct custom mappings.
 * Provides conversion methods for common types like UUID and LocalDateTime.
 */
@Component
public class MapperUtils {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Convert UUID to String
     */
    public String uuidToString(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    /**
     * Convert String to UUID
     */
    public UUID stringToUuid(String uuid) {
        return uuid == null ? null : UUID.fromString(uuid);
    }

    /**
     * Convert LocalDateTime to ISO-8601 String
     */
    public String localDateTimeToString(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(ISO_FORMATTER);
    }

    /**
     * Convert ISO-8601 String to LocalDateTime
     */
    public LocalDateTime stringToLocalDateTime(String dateTimeStr) {
        return dateTimeStr == null ? null : LocalDateTime.parse(dateTimeStr, ISO_FORMATTER);
    }

    /**
     * Convert LocalDateTime to ISO-8601 formatted string for JSON
     */
    public String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(ISO_FORMATTER);
    }
}
