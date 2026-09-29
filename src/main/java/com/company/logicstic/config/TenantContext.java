package com.company.logicstic.config;

import java.util.Optional;

import org.springframework.util.StringUtils;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        CURRENT_TENANT.set(tenantId);
    }

    public static Optional<String> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static String requireTenantId() {
        return getTenantId()
                .orElseThrow(() -> new IllegalStateException("No tenant is bound to the current request"));
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
