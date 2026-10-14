package com.company.logicstic.exception;

import com.company.logicstic.controller.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.TruckService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pins normalized pagination at the real controller/service boundary. */
class PaginationHttpContractTest {
    @ParameterizedTest @CsvSource({"0,0,1,1", "-5,-10,1,1", "2,1000,2,100", "1,20,1,20"})
    void numericPaginationMatchesFrontend(int page,int size,int expectedPage,int expectedSize) throws Exception {
        var repository=mock(TruckRepository.class);
        when(repository.search(any(),any(),any(),any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(3), 0));
        var mvc=MockMvcBuilders.standaloneSetup(new TruckController(new TruckService(repository,mock(EmployeeRepository.class))))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/trucks").param("page",String.valueOf(page)).param("pageSize",String.valueOf(size)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentPage").value(expectedPage))
                .andExpect(jsonPath("$.data.pageSize").value(expectedSize));
    }
    @Test void invalidSortAndInvalidTypesCannotReachRepository() throws Exception {
        var repository=mock(TruckRepository.class);
        var mvc=MockMvcBuilders.standaloneSetup(new TruckController(new TruckService(repository,mock(EmployeeRepository.class))))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for(String sort:List.of("unknown"," ","number desc;drop table trucks","customer.secret")) {
            mvc.perform(get("/api/trucks").param("orderBy",sort)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SORT_FIELD")).andExpect(jsonPath("$.errors[0].field").value("orderBy"));
        }
        for(String page:List.of("NaN","abc","1.5","999999999999999"))
            mvc.perform(get("/api/trucks").param("page",page)).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void invalidReportMonthFailsBeforeCalculators() throws Exception {
        var constructor=ReportController.class.getConstructors()[0];
        Object[] deps=Arrays.stream(constructor.getParameterTypes()).map(type->mock(type)).toArray();
        var controller=(ReportController)constructor.newInstance(deps);
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/reports/financials/monthly").param("year","2026").param("month","13"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("month"));
        verifyNoInteractions(deps);
    }
}
