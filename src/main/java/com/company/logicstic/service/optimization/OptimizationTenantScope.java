package com.company.logicstic.service.optimization;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** No implicit tenant/default datasource identity in external provider requests. */
@Component
public class OptimizationTenantScope {
    private final boolean tenancyEnabled;
    private final String explicitSingleTenantScope;
    public OptimizationTenantScope(@Value("${app.tenancy.enabled:true}") boolean tenancyEnabled,
                                   @Value("${app.optimization.single-tenant-scope:}") String explicitSingleTenantScope) {
        this.tenancyEnabled=tenancyEnabled; this.explicitSingleTenantScope=explicitSingleTenantScope;
    }
    public String require() {
        String scope = tenancyEnabled ? TenantContext.getTenantId().orElse(null) : explicitSingleTenantScope;
        if(scope==null || scope.isBlank() || !scope.equals(scope.trim()) || scope.length()>200
                || scope.indexOf('\r')>=0 || scope.indexOf('\n')>=0)
            throw new BadRequestException("OPTIMIZATION_TENANT_SCOPE_REQUIRED","Explicit bound tenant identity is required for qualified provider evidence");
        return scope;
    }
}
