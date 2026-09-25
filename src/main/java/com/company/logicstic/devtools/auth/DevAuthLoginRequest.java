package com.company.logicstic.devtools.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DevAuthLoginRequest(@NotBlank @Email String username, @NotBlank String password) {}
