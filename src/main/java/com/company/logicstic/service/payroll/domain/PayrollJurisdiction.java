package com.company.logicstic.service.payroll.domain;
import com.company.logicstic.exception.BadRequestException;
import java.util.Locale;
import java.util.Set;
/** Administrative subdivisions are optional and country-neutral. */
public record PayrollJurisdiction(String countryCode, String subdivisionCode, String localityCode) {
    public PayrollJurisdiction {
        if(countryCode==null || !Set.of(Locale.getISOCountries()).contains(countryCode.trim().toUpperCase(Locale.ROOT)))
            throw new BadRequestException("PAYROLL_COUNTRY_INVALID","An ISO alpha-2 country code is required");
        countryCode=countryCode.trim().toUpperCase(Locale.ROOT);
        subdivisionCode=optional(subdivisionCode,80); localityCode=optional(localityCode,120);
    }
    private static String optional(String s,int max) {
        if(s==null || s.isBlank()) return null;
        if(s.trim().length()>max) throw new BadRequestException("PAYROLL_JURISDICTION_INVALID","Jurisdiction code exceeds persisted length");
        return s.trim().toUpperCase(Locale.ROOT);
    }
}
