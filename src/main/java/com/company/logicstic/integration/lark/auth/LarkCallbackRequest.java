package com.company.logicstic.integration.lark.auth;

public record LarkCallbackRequest(
    String code,
    String state,
    String error,
    String errorDescription,
    String returnTo) {}
