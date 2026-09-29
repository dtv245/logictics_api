package com.company.logicstic.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.tenancy")
public class TenancyProperties {

    private boolean enabled;

    private Registry registry = new Registry();

    private Pool pool = new Pool();

    private Migration migration = new Migration();

    private Provisioning provisioning = new Provisioning();

    @Getter
    @Setter
    public static class Registry {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "org.postgresql.Driver";
        private String encryptionKey;
        private boolean autoInitialize = true;
    }

    @Getter
    @Setter
    public static class Pool {
        private int maxTenantPools = 100;
        private int maximumPoolSize = 10;
        private int minimumIdle = 1;
        private long connectionTimeoutMs = 30_000L;
        private long idleTimeoutMs = 600_000L;
    }

    @Getter
    @Setter
    public static class Migration {
        private List<String> locations = new ArrayList<>(List.of("classpath:db/migration/tenant"));
    }

    @Getter
    @Setter
    public static class Provisioning {
        private String adminUrl;
        private String adminUsername;
        private String adminPassword;
        private String templateDatabase;
    }
}
