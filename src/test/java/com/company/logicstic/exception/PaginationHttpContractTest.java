package com.company.logicstic.exception;

import com.company.logicstic.controller.*;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.mapper.PaymentMapper;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.PaymentService;
import com.company.logicstic.service.TruckService;
import com.company.logicstic.service.CurrentUserService;
import com.company.logicstic.service.payroll.PayPeriodService;
import com.company.logicstic.service.rating.RatingFingerprintService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
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

    @Test void paymentPageUsesSharedBoundsStableSortAndHasNextMetadata() throws Exception {
        var repository=mock(PaymentRepository.class);
        when(repository.search(any(),any(),any(Pageable.class))).thenAnswer(call ->
                org.springframework.data.domain.Page.empty(call.getArgument(2)));
        var service=new PaymentService(repository,mock(InvoiceRepository.class),mock(PaymentMapper.class),
                mock(CurrentUserService.class),mock(PaymentCommandRepository.class),mock(RatingFingerprintService.class));
        var mvc=MockMvcBuilders.standaloneSetup(new PaymentController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/payments").param("page","0").param("pageSize","1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPage").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(100))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        var pageable=org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(isNull(),isNull(),pageable.capture());
        assertEquals(0,pageable.getValue().getPageNumber());
        assertEquals(100,pageable.getValue().getPageSize());
        assertEquals(List.of("recordedAt","id"),pageable.getValue().getSort().stream()
                .map(org.springframework.data.domain.Sort.Order::getProperty).toList());

        mvc.perform(get("/api/payments").param("orderBy","unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT_FIELD"));
        verifyNoMoreInteractions(repository);
    }

    @Test void pagedResponseIncludesHasNextWithoutChangingExistingFields() {
        var page = new org.springframework.data.domain.PageImpl<>(
                List.of("first"),
                org.springframework.data.domain.PageRequest.of(0,1),
                2
        );
        var response = PagedResponse.from(page);
        assertEquals(List.of("first"),response.items());
        assertEquals(2,response.totalItems());
        assertEquals(1,response.currentPage());
        assertEquals(1,response.pageSize());
        assertTrue(response.hasNext());
    }

    @Test void legacyListWrapperKeepsArrayDataAndAddsBoundedPaginationMetadata() throws Exception {
        var service = mock(PayPeriodService.class);
        when(service.list(any(Pageable.class))).thenAnswer(call ->
                org.springframework.data.domain.Page.empty(call.getArgument(0)));
        var mvc = MockMvcBuilders.standaloneSetup(new PayPeriodController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/pay-periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.pagination.page").value(1))
                .andExpect(jsonPath("$.meta.pagination.size").value(20));

        mvc.perform(get("/api/pay-periods").param("page", "3").param("pageSize", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.pagination.page").value(3))
                .andExpect(jsonPath("$.meta.pagination.size").value(50))
                .andExpect(jsonPath("$.meta.pagination.hasNext").value(false));

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(service, times(2)).list(pageable.capture());
        assertEquals(List.of(20, 50), pageable.getAllValues().stream().map(Pageable::getPageSize).toList());
        assertEquals(List.of("startDate", "id"), pageable.getValue().getSort().stream()
                .map(org.springframework.data.domain.Sort.Order::getProperty).toList());
    }
}
