package com.company.logicstic.common;

import com.company.logicstic.dto.ApiError;
import com.company.logicstic.exception.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Validates ordinary financial calendar periods before date arithmetic or database reads. */
public final class ReportPeriodValidation {
    private ReportPeriodValidation() {}
    public static void monthly(int year,int month) {
        List<ApiError> errors=new ArrayList<>();
        if(year<1||year>9999) errors.add(new ApiError("year","INVALID_VALUE","Year must be between 1 and 9999",Map.of("min",1,"max",9999)));
        if(month<1||month>12) errors.add(new ApiError("month","INVALID_VALUE","Month must be between 1 and 12",Map.of("min",1,"max",12)));
        if(!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REPORT_PERIOD","Report period is invalid",errors,Map.of());
    }
}
