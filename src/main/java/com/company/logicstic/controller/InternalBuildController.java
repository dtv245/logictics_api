package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Packaged identity only: no environment, registry, credentials or connection details. */
@RestController
public class InternalBuildController {
    private final ObjectProvider<BuildProperties> builds;
    public InternalBuildController(ObjectProvider<BuildProperties> builds) { this.builds = builds; }
    public record Identity(String version, String sourceCommit, String sourceHash, String buildId,
            boolean dirty, Instant builtAt) {}

    @GetMapping("/api/internal/build")
    public ApiResponse<Identity> build(HttpServletRequest request) {
        var build = builds.getIfAvailable();
        if (build == null)
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BUILD_METADATA_MISSING", "Packaged build metadata is unavailable");
        return ApiResponse.success(new Identity(build.getVersion(), value(build, "source.commit", "UNKNOWN"),
                value(build, "source.hash", "UNKNOWN"), value(build, "id", "UNVERIFIED_LOCAL_BUILD"),
                Boolean.parseBoolean(value(build, "source.dirty", "true")), build.getTime()), request);
    }
    private String value(BuildProperties build, String name, String fallback) {
        String value = build.get(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
