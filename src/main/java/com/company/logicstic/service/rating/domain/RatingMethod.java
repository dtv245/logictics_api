package com.company.logicstic.service.rating.domain;

/** Business-approved V1 linehaul methods; advanced formulas are deliberately absent. */
public enum RatingMethod {
    FLAT, PER_MILE;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static RatingMethod from(String value) {
        for (var method : values()) if (method.name().equals(value)) return method;
        throw new com.company.logicstic.exception.BadRequestException(
                "UNSUPPORTED_RATE_METHOD", "Rating V1 supports only FLAT and PER_MILE linehaul");
    }
}
