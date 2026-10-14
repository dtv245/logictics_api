package com.company.logicstic.common;

import com.company.logicstic.dto.ApiError;
import com.company.logicstic.exception.ApiException;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/** Maps public sort fields to explicit entity paths, never to arbitrary query expressions. */
public final class QuerySortPolicy {
    private static final Map<String,Map<String,String>> FIELDS = Map.ofEntries(
            Map.entry("customers", fields("id","name","email","status")),
            Map.entry("employees", fields("id","firstName","lastName","email","status")),
            Map.entry("trucks", fields("id","number","type","status","licensePlate")),
            Map.entry("loads", Map.of("id","id","number","number","name","name","status","status","customerId","customer.id")),
            Map.entry("trips", fields("id","number","name","status","totalDistance")),
            Map.entry("invoices", fields("id","number","type","status","dueDate")),
            Map.entry("payments", fields("id","recordedAt","status","referenceNumber")),
            Map.entry("documents", fields("id","fileName","type","status")),
            Map.entry("inspections", fields("id","inspectedAt","type")));
    private QuerySortPolicy() {}
    private static Map<String,String> fields(String... names) {
        Map<String,String> map=new LinkedHashMap<>(); for(String name:names)map.put(name,name); return Map.copyOf(map);
    }
    public static Sort sort(String resource, String publicField, boolean descending) {
        var fields=FIELDS.get(resource);
        if (fields==null) throw new IllegalStateException("Sort policy is missing for resource");
        String property=publicField==null?null:fields.get(publicField);
        if(property==null) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SORT_FIELD","Sort field is not supported",
                List.of(new ApiError("orderBy","INVALID_SORT_FIELD","Choose a supported sort field")),Map.of());
        return Sort.by(descending?Sort.Direction.DESC:Sort.Direction.ASC,property);
    }
}
