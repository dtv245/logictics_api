package com.company.logicstic.config;

import java.util.Set;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

/** Keep health and the real security chain available without creating DB services. */
public class NoDbComponentFilter implements TypeFilter, EnvironmentAware {
    private static final Set<String> AVAILABLE_COMPONENTS = Set.of(
            "com.company.logicstic.LogisticApplication",
            "com.company.logicstic.config.SecurityConfig",
            "com.company.logicstic.controller.HealthController",
            "com.company.logicstic.exception.GlobalExceptionHandler",
            "com.company.logicstic.integration.lark.config.LarkConfiguration",
            "com.company.logicstic.integration.lark.auth.LarkAuthClient",
            "com.company.logicstic.integration.lark.auth.LarkAuthController",
            "com.company.logicstic.integration.lark.auth.LarkAuthService",
            "com.company.logicstic.integration.lark.auth.LarkAuthenticationFilter");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean match(MetadataReader metadata, MetadataReaderFactory factory) {
        String name = metadata.getClassMetadata().getClassName();
        return environment != null && environment.acceptsProfiles(Profiles.of("nodb"))
                && name.startsWith("com.company.logicstic.")
                && !AVAILABLE_COMPONENTS.contains(name);
    }
}
