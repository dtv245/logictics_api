package com.company.logicstic.config;

import java.net.URI;
import org.springframework.util.StringUtils;

public final class DatabaseUrlUtils {

    private DatabaseUrlUtils() {}

    public record NormalizedJdbcUrl(String jdbcUrl, String username, String password) {}

    public static NormalizedJdbcUrl normalize(String rawUrl, String defaultUsername, String defaultPassword) {
        if (!StringUtils.hasText(rawUrl)) {
            return new NormalizedJdbcUrl(rawUrl, defaultUsername, defaultPassword);
        }
        String trimmed = rawUrl.trim();
        if (trimmed.startsWith("jdbc:postgresql:") || trimmed.startsWith("jdbc:h2:")) {
            return new NormalizedJdbcUrl(trimmed, defaultUsername, defaultPassword);
        }
        if (trimmed.startsWith("postgres://") || trimmed.startsWith("postgresql://")) {
            try {
                URI uri = URI.create(trimmed);
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();
                String query = uri.getQuery();
                String userInfo = uri.getUserInfo();

                String extractedUser = defaultUsername;
                String extractedPass = defaultPassword;
                if (StringUtils.hasText(userInfo)) {
                    String[] parts = userInfo.split(":", 2);
                    if (!StringUtils.hasText(extractedUser) && parts.length > 0 && StringUtils.hasText(parts[0])) {
                        extractedUser = parts[0];
                    }
                    if (!StringUtils.hasText(extractedPass) && parts.length > 1 && StringUtils.hasText(parts[1])) {
                        extractedPass = parts[1];
                    }
                }

                StringBuilder jdbc = new StringBuilder("jdbc:postgresql://");
                if (StringUtils.hasText(host)) {
                    jdbc.append(host);
                }
                if (port > 0) {
                    jdbc.append(":").append(port);
                }
                if (StringUtils.hasText(path)) {
                    jdbc.append(path);
                }
                if (StringUtils.hasText(query)) {
                    jdbc.append("?").append(query);
                }
                return new NormalizedJdbcUrl(jdbc.toString(), extractedUser, extractedPass);
            } catch (Exception ignored) {
                String withoutScheme = trimmed.replaceFirst("^postgres(ql)?://", "");
                return new NormalizedJdbcUrl("jdbc:postgresql://" + withoutScheme, defaultUsername, defaultPassword);
            }
        }
        if (trimmed.startsWith("jdbc:")) {
            return new NormalizedJdbcUrl(trimmed, defaultUsername, defaultPassword);
        }
        return new NormalizedJdbcUrl("jdbc:postgresql://" + trimmed, defaultUsername, defaultPassword);
    }
}
