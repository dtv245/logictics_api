package com.company.logicstic.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;

/** One validated correlation ID shared by HTTP transport and domain logging. */
public final class RequestIds {
    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = RequestIds.class.getName() + ".id";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1,120}");
    private RequestIds() {}
    public static String getOrCreate(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof String id && VALID.matcher(id).matches()) return id;
        String supplied = request.getHeader(HEADER);
        String id = supplied != null && VALID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        request.setAttribute("correlationId", id);
        return id;
    }
}
