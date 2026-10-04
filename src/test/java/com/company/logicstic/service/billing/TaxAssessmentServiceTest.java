package com.company.logicstic.service.billing;

import com.company.logicstic.dto.invoice.TaxAssessmentRequest;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.rating.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaxAssessmentServiceTest {
    private final TaxAssessmentService service=new TaxAssessmentService(mock(TaxAssessmentRepository.class),
            mock(EmployeeRepository.class),mock(LoadRepository.class),new CurrencyScaleProvider(),
            new RatingFingerprintService(new tools.jackson.databind.ObjectMapper()));
    private TaxAssessmentRequest request(String currency,String tax,String source) {
        return new TaxAssessmentRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),source,"accounting assessment",
                "explicit jurisdiction",new BigDecimal("100"),new BigDecimal(tax),currency,null,
                OffsetDateTime.parse("2026-01-12T09:00:00+07:00"),"accounting assessor");
    }
    @Test void retainsZeroAssessedTaxAndOriginalAssessmentAudit() {
        var a=request("USD","0","ACCOUNTING");var validated=service.validate(a);
        assertEquals(BigDecimal.ZERO,validated.taxAmount());assertEquals(a.assessedAt().toInstant(),validated.assessedAt().toInstant());
        assertEquals(a.sourceReference(),validated.sourceReference());assertNull(validated.policyVersion());
    }
    @Test void currencyPrecisionIsNotGlobalTwoOrSilentlyRounded() {
        assertEquals(new BigDecimal("1.123"),service.validate(request("KWD","1.123","ACCOUNTING")).taxAmount());
        assertEquals("INVALID_TAX_ASSESSMENT",assertThrows(ApiException.class,()->service.validate(request("USD","1.123","ACCOUNTING"))).getCode());
    }
    @Test void missingNegativeAndUnqualifiedAssessmentReject() {
        assertThrows(ApiException.class,()->service.validate(null));
        assertThrows(ApiException.class,()->service.validate(request("USD","-1","ACCOUNTING")));
        assertThrows(ApiException.class,()->service.validate(request("USD","1","INTERNAL_DEFAULT_ENGINE")));
    }
    @Test void providerLabelNeverSubstitutesPersistedAccountingCaptureActor() {
        assertThrows(ApiException.class,()->service.capture(request("USD","1","TRUSTED_EXTERNAL_TAX_PROVIDER"),null));
    }
}
